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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
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

    /** The notice's argument: the liquid's name, or for the locked oil the fluid's id. */
    private static void say(ServerPlayer player, Notice notice, PipeKind pipe, Found found) {
        Component argument = notice == Notice.OIL_LOCKED && found.usable()
                ? Component.literal(found.inventory().fluids().lockedFluid())
                : liquidName(pipe);
        PipeNotices.send(player, notice, argument);
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
     * Left click: pours a bucket from the matching tank into the world, where a bucket would put it (into the clicked block if it can hold the fluid,
     * else into the cell next to the clicked face). The caller cancels the click so that nothing is mined. Not where the same source already is.
     */
    public static Result place(ServerPlayer player, PipeKind pipe, BlockPos pos, Direction face) {
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
