package pnnh.mcfloatingbackpack.pipe;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.network.BackpackNetwork;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Action;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Notice;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Plan;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Situation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import pnnh.mcfloatingbackpack.tank.FluidStore;

/**
 * 049: what a pipe does in the world, on the server. It finds the backpack the B key would open, lets {@link PipePlanner} decide, changes the
 * world the way a bucket does, and only if that worked keeps the new liquid in the backpack (saved, and sent to the panel). The world and
 * the backpack's liquid change together or not at all.
 */
public final class PipeActions {
    /** The fluids that count as crude oil (NeoForge defines none; mods that add oil put theirs here). */
    public static final TagKey<Fluid> CRUDE_OIL = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "crude_oil"));

    /** A beehive's honey is ready to take at this level (vanilla's own: a glass bottle needs it too). */
    public static final int READY_HONEY_LEVEL = 5;

    /** How a try went, for the item's answer: nothing to do with this pipe, refused, or done. */
    public enum Result { NOT_FOR_THIS_PIPE, REFUSED, DONE }

    private PipeActions() {
    }

    /** The backpack the B key would open, with its working copy; or why there is none. */
    private record Found(BackpackRef ref, BackpackInventory inventory, boolean protectedOne) {
        boolean usable() {
            return inventory != null;
        }
    }

    private static Found find(ServerPlayer player) {
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null || !ref.stillValid(player)) {
            return new Found(null, null, false);
        }
        // 026/040: the registry decides first; a backpack a newer version saved is not touched
        Optional<BackpackInventory> acquired = BackpackGate.acquire(player, ref);
        if (acquired.isEmpty()) {
            return new Found(ref, null, true);
        }
        return new Found(ref, acquired.get(), false);
    }

    private static Situation situation(PipeKind pipe, Found found, String fluidId) {
        if (!found.usable()) {
            return new Situation(false, found.protectedOne(), null, FluidStore.EMPTY, fluidId);
        }
        return new Situation(true, false, found.inventory().tankTier(pipe.fluid()), found.inventory().fluids(), fluidId);
    }

    private static Component liquidName(PipeKind pipe) {
        return Component.translatable("fluid.mcfloatingbackpack." + pipe.fluid().id());
    }

    /** The notice's argument: the liquid's name; for the locked oil (or an oil with no bucket) the fluid's id; for a missing container the container's name. */
    private static void say(ServerPlayer player, Notice notice, PipeKind pipe, Found found) {
        Component argument;
        if ((notice == Notice.OIL_LOCKED || notice == Notice.NO_BUCKET_FOR_FLUID) && found.usable()) {
            argument = Component.literal(found.inventory().fluids().lockedFluid());
        } else if (notice == Notice.NO_EMPTY_CONTAINER) {
            argument = emptyContainerItem(pipe).getDescription();
        } else {
            argument = liquidName(pipe);
        }
        PipeNotices.send(player, notice, argument);
    }

    /** The empty container a pipe's discharge uses up: an empty bucket, or an empty glass bottle for honey. */
    private static Item emptyContainerItem(PipeKind pipe) {
        return pipe.emptyContainer() == PipeKind.Container.GLASS_BOTTLE ? Items.GLASS_BOTTLE : Items.BUCKET;
    }

    /**
     * What a discharge makes: a water, lava or milk bucket, a honey bottle, or for oil the bucket of the fluid the tank is locked to; empty when
     * there is none (an oil whose fluid has no bucket item, or is not registered any more).
     */
    private static ItemStack productOf(PipeKind pipe, String lockedFluidId) {
        switch (pipe) {
            case WATER:
                return new ItemStack(Items.WATER_BUCKET);
            case LAVA:
                return new ItemStack(Items.LAVA_BUCKET);
            case MILK:
                return new ItemStack(Items.MILK_BUCKET);
            case HONEY:
                return new ItemStack(Items.HONEY_BOTTLE);
            default:
                Fluid fluid = fluidToPlace(pipe, lockedFluidId);
                Item bucket = fluid == null ? Items.AIR : fluid.getBucket();
                return bucket == Items.AIR ? ItemStack.EMPTY : new ItemStack(bucket);
        }
    }

    /** Keeps the new liquid: into the working copy, saved to the backpack, and told to the panel. */
    private static void commit(ServerPlayer player, Found found, FluidStore next) {
        found.inventory().setFluids(next);
        found.ref().saveInventory(player, found.inventory());
        BackpackNetwork.sendFluids(player, found.inventory());
    }

    /**
     * Right click: takes a bucket of the pipe's liquid from the source block the player looks at (the way an empty bucket does), into the
     * matching tank. The caller has already checked that the player may interact there.
     */
    public static Result absorb(ServerPlayer player, PipeKind pipe, BlockHitResult hit) {
        Level level = player.level();
        BlockPos pos = hit.getBlockPos();
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.isSource()) {
            return Result.NOT_FOR_THIS_PIPE;
        }
        if (!pipe.accepts(fluid.is(FluidTags.WATER), fluid.is(FluidTags.LAVA), fluid.is(CRUDE_OIL))) {
            return Result.NOT_FOR_THIS_PIPE;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BucketPickup pickup)) {
            return Result.NOT_FOR_THIS_PIPE;
        }
        Fluid source = fluid.getType();
        String fluidId = BuiltInRegistries.FLUID.getKey(source).toString();

        Found found = find(player);
        Plan plan = PipePlanner.absorb(pipe, situation(pipe, found, fluidId));
        if (plan.action() == Action.REFUSE) {
            say(player, plan.notice(), pipe, found);
            return Result.REFUSED;
        }

        // the world first: only if the source is really gone is the liquid kept
        ItemStack taken = pickup.pickupBlock(player, level, pos, state);
        FluidState after = level.getFluidState(pos);
        boolean changed = !taken.isEmpty() || !after.isSource() || after.getType() != source;
        if (!changed) {
            return Result.REFUSED;
        }
        commit(player, found, plan.next());
        pickup.getPickupSound(state).ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        say(player, plan.notice(), pipe, found);
        return Result.DONE;
    }

    /** The fluid a pipe pours out: its own for water and lava, the fluid the oil is locked to for oil; {@code null} if that fluid is not registered. */
    private static Fluid fluidToPlace(PipeKind pipe, String fluidId) {
        switch (pipe) {
            case WATER:
                return Fluids.WATER;
            case LAVA:
                return Fluids.LAVA;
            default:
                ResourceLocation id = ResourceLocation.tryParse(fluidId);
                Fluid fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(id);
                return fluid == Fluids.EMPTY ? null : fluid;
        }
    }

    /**
     * A bucket's worth of one fluid that can be taken once: what {@link FluidUtil#tryPlaceFluid} drains from, standing in for the tank. It only
     * records that the world took it; the liquid is kept in the backpack by {@link #commit} after that.
     */
    private static final class OneBucket implements IFluidHandler {
        private final Fluid fluid;
        private boolean taken;

        OneBucket(Fluid fluid) {
            this.fluid = fluid;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return taken ? FluidStack.EMPTY : new FluidStack(fluid, PipePlanner.BUCKET_MB);
        }

        @Override
        public int getTankCapacity(int tank) {
            return PipePlanner.BUCKET_MB;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.getFluid() == fluid;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (taken || resource.getFluid() != fluid) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (taken || maxDrain < PipePlanner.BUCKET_MB) {
                return FluidStack.EMPTY;
            }
            if (action.execute()) {
                taken = true;
            }
            return new FluidStack(fluid, PipePlanner.BUCKET_MB);
        }
    }

    /**
     * Right click on an adult cow or goat with the milk pipe: milks it into the milk tank (the animal is not touched). The caller has checked that it
     * is such an animal and that the player is not sneaking.
     */
    public static Result absorbEntity(ServerPlayer player, PipeKind pipe, LivingEntity target) {
        Found found = find(player);
        Plan plan = PipePlanner.absorb(pipe, situation(pipe, found, ""));
        if (plan.action() == Action.REFUSE) {
            say(player, plan.notice(), pipe, found);
            return Result.REFUSED;
        }
        // nothing in the world changes: the liquid is kept, with the sound of milking (a goat's own, a screaming goat's own)
        SoundEvent sound = target instanceof Goat goat
                ? (goat.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK)
                : SoundEvents.COW_MILK;
        commit(player, found, plan.next());
        player.playSound(sound, 1.0F, 1.0F);
        say(player, plan.notice(), pipe, found);
        return Result.DONE;
    }

    /**
     * Right click on a beehive or bee nest whose honey is ready (honey level 5) with the honey pipe: takes a bottle's worth of honey, the way a glass
     * bottle does. Without a campfire's smoke below the hive the bees inside come out and the ones near are angry (vanilla's own: the two small
     * private parts of it are written out here, the public ones are used as they are); the honey level goes back to 0.
     */
    public static Result absorbHive(ServerPlayer player, PipeKind pipe, BlockPos pos, BlockState state) {
        Level level = player.level();
        if (!(state.getBlock() instanceof BeehiveBlock hive) || state.getValue(BeehiveBlock.HONEY_LEVEL) < READY_HONEY_LEVEL) {
            return Result.NOT_FOR_THIS_PIPE;
        }
        Found found = find(player);
        Plan plan = PipePlanner.absorb(pipe, situation(pipe, found, ""));
        if (plan.action() == Action.REFUSE) {
            say(player, plan.notice(), pipe, found);
            return Result.REFUSED;
        }
        // the world first (vanilla's way of taking the honey): only if the hive was emptied is the liquid kept
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        if (!CampfireBlock.isSmokeyPos(level, pos)) {
            if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity entity && !entity.isEmpty()) {
                angerNearbyBees(level, pos);
            }
            hive.releaseBeesAndResetHoneyLevel(level, state, pos, player, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        } else {
            hive.resetHoneyLevel(level, state, pos);
        }
        if (level.getBlockState(pos).getValue(BeehiveBlock.HONEY_LEVEL) >= READY_HONEY_LEVEL) {
            return Result.REFUSED;
        }
        commit(player, found, plan.next());
        say(player, plan.notice(), pipe, found);
        return Result.DONE;
    }

    /** Vanilla's {@code BeehiveBlock.angerNearbyBees} (private there): the bees near the hive that have no target go for one of the players near it. */
    private static void angerNearbyBees(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(8.0, 6.0, 8.0);
        java.util.List<Bee> bees = level.getEntitiesOfClass(Bee.class, area);
        if (bees.isEmpty()) {
            return;
        }
        java.util.List<Player> players = level.getEntitiesOfClass(Player.class, area);
        if (players.isEmpty()) {
            return;
        }
        for (Bee bee : bees) {
            if (bee.getTarget() == null) {
                bee.setTarget(players.get(level.random.nextInt(players.size())));
            }
        }
    }

    /** The stack at a position of {@link ContainerPicker}: a cell of the backpack's main area, then the player's inventory. */
    private static ItemStack stackAt(Found found, Inventory inventory, int mainSize, int position) {
        return ContainerPicker.isInBackpack(position, mainSize)
                ? found.inventory().getStackInSlot(position)
                : inventory.items.get(ContainerPicker.inventoryIndex(position, mainSize));
    }

    /**
     * Sneak + right click: fills an empty container (a bucket, or a glass bottle for honey) from the matching tank, whatever the player points at.
     * The empty container comes from the backpack's main area first, then the inventory; the full one goes into the inventory, or on the ground
     * if there is no room. Everything is checked first ({@link PipePlanner#discharge}), then done.
     */
    public static Result discharge(ServerPlayer player, PipeKind pipe) {
        Level level = player.level();
        Found found = find(player);
        Inventory inventory = player.getInventory();
        int mainSize = found.usable() ? found.inventory().size() : 0;
        Item emptyItem = emptyContainerItem(pipe);
        int position = ContainerPicker.firstMatch(
                mainSize, inventory.items.size(), p -> stackAt(found, inventory, mainSize, p).is(emptyItem));
        ItemStack product = productOf(pipe, found.usable() ? found.inventory().fluids().lockedFluid() : "");
        Plan plan = PipePlanner.discharge(pipe, situation(pipe, found, ""), new PipePlanner.Supply(position >= 0, !product.isEmpty()));
        if (plan.action() == Action.REFUSE) {
            say(player, plan.notice(), pipe, found);
            return Result.REFUSED;
        }
        // the container and the product first, then the liquid
        ItemStack taken = stackAt(found, inventory, mainSize, position);
        taken.shrink(1);
        boolean fromBackpack = ContainerPicker.isInBackpack(position, mainSize);
        if (fromBackpack) {
            found.inventory().setStackInSlot(position, taken.isEmpty() ? ItemStack.EMPTY : taken);
        }
        if (!inventory.add(product.copy())) {
            player.drop(product.copy(), false);
        }
        commit(player, found, plan.next());
        if (fromBackpack) {
            BackpackNetwork.sendFullSync(player, found.ref());
        }
        level.playSound(null, player.blockPosition(), pipe == PipeKind.HONEY ? SoundEvents.BOTTLE_FILL : SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PICKUP, player.blockPosition());
        say(player, plan.notice(), pipe, found);
        return Result.DONE;
    }

    /**
     * Left click: pours a bucket from the matching tank into the world, where a bucket would put it (into the clicked block if it can hold the fluid,
     * else into the cell next to the clicked face). The caller cancels the click so that nothing is mined. Not where the same source already is.
     */
    public static Result place(ServerPlayer player, PipeKind pipe, BlockPos pos, Direction face) {
        // milk and honey have no block to pour out: say so, without looking for the backpack
        if (!pipe.hasBlockForm()) {
            PipeNotices.send(player, Notice.NO_BLOCK_FORM, liquidName(pipe));
            return Result.REFUSED;
        }
        Level level = player.level();
        Found found = find(player);
        Plan plan = PipePlanner.place(pipe, situation(pipe, found, ""));
        if (plan.action() == Action.REFUSE) {
            say(player, plan.notice(), pipe, found);
            return Result.REFUSED;
        }
        Fluid fluid = fluidToPlace(pipe, plan.fluidId());
        if (fluid == null) {
            PipeNotices.send(player, Notice.NO_FLUID, Component.literal(plan.fluidId()));
            return Result.REFUSED;
        }
        ItemStack stack = player.getMainHandItem();
        BlockState clicked = level.getBlockState(pos);
        boolean intoClicked = clicked.getBlock() instanceof LiquidBlockContainer container && container.canPlaceLiquid(player, level, pos, clicked, fluid);
        BlockPos target = intoClicked ? pos : pos.relative(face);
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(target, face, stack)) {
            return Result.REFUSED;
        }
        // a source of this fluid is already there: pouring more would use up a bucket for nothing (a held button must not drain the tank)
        FluidState there = level.getFluidState(target);
        if (there.isSource() && there.getType() == fluid) {
            return Result.REFUSED;
        }
        // the world first (the bucket's own way: nether evaporation, waterlogging, sound): only if it took the bucket is the liquid taken out of the backpack
        OneBucket bucket = new OneBucket(fluid);
        boolean placed = FluidUtil.tryPlaceFluid(player, level, InteractionHand.MAIN_HAND, target, bucket, new FluidStack(fluid, PipePlanner.BUCKET_MB));
        if (!placed || !bucket.taken) {
            return Result.REFUSED;
        }
        commit(player, found, plan.next());
        level.gameEvent(player, GameEvent.FLUID_PLACE, target);
        say(player, plan.notice(), pipe, found);
        return Result.DONE;
    }
}
