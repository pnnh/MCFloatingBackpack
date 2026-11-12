package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 049: the wiring no pure test can see because the classes need Minecraft, and what was not touched. */
class PipeWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    // ---- right click: absorbing ----

    @Test
    void theItemAbsorbsOnTheServerLikeAnEmptyBucket() throws IOException {
        String item = read(SRC.resolve("item/PipeItem.java"));
        String use = item.substring(item.indexOf("public InteractionResultHolder<ItemStack> use("));
        assertTrue(use.contains("level.isClientSide") && use.contains("hand != InteractionHand.MAIN_HAND"), "the server, the main hand");
        assertTrue(use.contains("player.isShiftKeyDown()"), "sneaking is kept for 050");
        int look = use.indexOf("getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY)");
        int may = use.indexOf("level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(face), face, stack)");
        int absorb = use.indexOf("PipeActions.absorb(serverPlayer, kind, hit)");
        assertTrue(look > 0 && may > look && absorb > may, "look through the water, may the player, then absorb");
        assertTrue(use.contains("NOT_FOR_THIS_PIPE -> InteractionResultHolder.pass(stack)"));
        assertTrue(use.contains("REFUSED -> InteractionResultHolder.fail(stack)"));
        assertTrue(use.contains("DONE -> InteractionResultHolder.success(stack)"));
        // (050 adds useOn for the honey pipe and interactLivingEntity for the milk pipe; both pass for everything else, so vanilla still goes on to use())
    }

    @Test
    void absorbingChecksTheFluidThenAsksThePlannerThenChangesTheWorldThenKeepsTheLiquid() throws IOException {
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        String absorb = actions.substring(actions.indexOf("public static Result absorb("));
        int source = absorb.indexOf("fluid.isSource()");
        int classify = absorb.indexOf("pipe.accepts(fluid.is(FluidTags.WATER), fluid.is(FluidTags.LAVA), fluid.is(CRUDE_OIL))");
        int bucket = absorb.indexOf("state.getBlock() instanceof BucketPickup pickup");
        int plan = absorb.indexOf("PipePlanner.absorb(pipe, situation(pipe, found, fluidId))");
        int pickup = absorb.indexOf("pickup.pickupBlock(player, level, pos, state)");
        int changed = absorb.indexOf("!taken.isEmpty() || !after.isSource() || after.getType() != source");
        int commit = absorb.indexOf("commit(player, found, plan.next())");
        assertTrue(source > 0 && classify > source && bucket > classify && plan > bucket && pickup > plan && changed > pickup && commit > changed,
                "source, which liquid, bucket-pickup, plan, world, did it change, keep the liquid");
        assertTrue(absorb.contains("GameEvent.FLUID_PICKUP") && absorb.contains("getPickupSound(state)"));
        String commitMethod = actions.substring(actions.indexOf("private static void commit("));
        int set = commitMethod.indexOf("setFluids(next)");
        int save = commitMethod.indexOf("saveInventory(player, found.inventory())");
        int send = commitMethod.indexOf("BackpackNetwork.sendFluids(player, found.inventory())");
        assertTrue(set > 0 && save > set && send > save, "kept, saved, told to the panel");
    }

    @Test
    void theBackpackIsFoundTheWayTheAutoPickupFindsIt() throws IOException {
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        int resolve = actions.indexOf("BackpackRef.resolve(player)");
        int valid = actions.indexOf("ref.stillValid(player)");
        int gate = actions.indexOf("BackpackGate.acquire(player, ref)");
        assertTrue(resolve > 0 && valid > resolve && gate > valid);
        assertTrue(actions.contains("inventory().tankTier(pipe.fluid())"), "the tank that counts is the one in the bottom area (048)");
    }

    @Test
    void noticesGoThroughTheCooldownAndAreOnlyForThePlayer() throws IOException {
        String notices = read(SRC.resolve("pipe/PipeNotices.java"));
        assertTrue(notices.contains("PipeCooldowns.noticeMayGo(player.getUUID(), notice, now)"));
        assertTrue(notices.contains("player.sendSystemMessage(") && notices.contains("message.mcfloatingbackpack.prefix"));
        assertTrue(notices.contains("getTickCount()"), "the server's tick count is the clock");
        String cooldowns = read(SRC.resolve("pipe/PipeCooldowns.java"));
        assertTrue(cooldowns.contains("NOTICE_TICKS = 20") && cooldowns.contains("PLACE_TICKS = 4"));
        assertTrue(read(SRC.resolve("MCFloatingBackpack.java")).contains("PipeCooldowns.forget(player.getUUID())"), "forgotten when the player leaves");
    }

    @Test
    void everyNoticeHasAKey() throws IOException {
        String notices = read(SRC.resolve("pipe/PipeNotices.java"));
        for (String name : new String[] {"NO_BACKPACK", "BACKPACK_PROTECTED", "NO_TANK", "OIL_LOCKED", "CANNOT_ABSORB_FULL", "CANNOT_PLACE_EMPTY", "TANK_FULL_NOW", "TANK_EMPTY_NOW", "NO_FLUID"}) {
            assertTrue(notices.contains("case " + name), name);
        }
    }

    // ---- left click: pouring ----

    @Test
    void theLeftButtonNeverMinesAndOnlyTheServersStartPours() throws IOException {
        String events = read(SRC.resolve("pipe/PipeEvents.java"));
        int kind = events.indexOf("PipeItem.kindOf(player.getMainHandItem())");
        int cancel = events.indexOf("event.setCanceled(true)");
        int side = events.indexOf("event.getLevel().isClientSide()");
        int start = events.indexOf("event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START");
        int cooldown = events.indexOf("PipeCooldowns.placementMayGo(serverPlayer.getUUID(), now)");
        int place = events.indexOf("PipeActions.place(serverPlayer, pipe, event.getPos(), face)");
        assertTrue(kind > 0 && cancel > kind && side > cancel && start > side && cooldown > start && place > cooldown,
                "main hand pipe, cancel (both sides), server, start only, not too soon, pour");
        assertTrue(read(SRC.resolve("MCFloatingBackpack.java")).contains("NeoForge.EVENT_BUS.addListener(pnnh.mcfloatingbackpack.pipe.PipeEvents::onLeftClickBlock)"));
    }

    @Test
    void pouringUsesTheBucketsOwnWayAndKeepsTheLiquidOnlyIfTheWorldTookIt() throws IOException {
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        String place = actions.substring(actions.indexOf("public static Result place("));
        int plan = place.indexOf("PipePlanner.place(pipe, situation(pipe, found, \"\"))");
        int fluid = place.indexOf("fluidToPlace(pipe, plan.fluidId())");
        int target = place.indexOf("intoClicked ? pos : pos.relative(face)");
        int may = place.indexOf("level.mayInteract(player, pos) || !player.mayUseItemAt(target, face, stack)");
        int same = place.indexOf("there.isSource() && there.getType() == fluid");
        int world = place.indexOf("FluidUtil.tryPlaceFluid(player, level, InteractionHand.MAIN_HAND, target, bucket, new FluidStack(fluid, PipePlanner.BUCKET_MB))");
        int took = place.indexOf("!placed || !bucket.taken");
        int commit = place.indexOf("commit(player, found, plan.next())");
        assertTrue(plan > 0 && fluid > plan && target > fluid && may > target && same > may && world > same && took > world && commit > took,
                "plan, fluid, where, may, not on the same source, world, did it take, keep the liquid");
        assertTrue(place.contains("LiquidBlockContainer") && place.contains("GameEvent.FLUID_PLACE"));
        assertTrue(actions.contains("class OneBucket implements IFluidHandler"));
        assertTrue(actions.contains("PipeNotices.send(player, Notice.NO_FLUID"), "oil whose mod is gone is said, not charged");
    }

    @Test
    void oilIsPouredAsTheFluidItIsLockedTo() throws IOException {
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        assertTrue(actions.contains("BuiltInRegistries.FLUID.get(id)") && actions.contains("fluid == Fluids.EMPTY ? null : fluid"));
        assertTrue(actions.contains("case WATER:\n                return Fluids.WATER;") && actions.contains("case LAVA:\n                return Fluids.LAVA;"));
        assertTrue(actions.contains("ResourceLocation.fromNamespaceAndPath(\"c\", \"crude_oil\")"));
    }

    // ---- 050: sneak + right click fills an empty container ----

    @Test
    void sneakingFillsAContainerAndNotSneakingKeepsTheOldWays() throws IOException {
        String item = read(SRC.resolve("item/PipeItem.java"));
        String use = item.substring(item.indexOf("public InteractionResultHolder<ItemStack> use("));
        int sneak = use.indexOf("player.isShiftKeyDown()");
        int discharge = use.indexOf("PipeActions.discharge(serverPlayer, kind)");
        int noBlock = use.indexOf("!kind.hasBlockForm()");
        int absorb = use.indexOf("PipeActions.absorb(serverPlayer, kind, hit)");
        assertTrue(sneak > 0 && discharge > sneak && noBlock > discharge && absorb > noBlock,
                "sneaking discharges; then a milk or honey pipe passes; then the others absorb");
    }

    @Test
    void dischargeChecksEverythingThenUsesUpTheContainerThenKeepsTheLiquid() throws IOException {
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        String discharge = actions.substring(actions.indexOf("public static Result discharge("), actions.indexOf("public static Result place("));
        int find = discharge.indexOf("Found found = find(player)");
        int pick = discharge.indexOf("ContainerPicker.firstMatch(");
        int product = discharge.indexOf("productOf(pipe,");
        int plan = discharge.indexOf("PipePlanner.discharge(pipe, situation(pipe, found, \"\"), new PipePlanner.Supply(position >= 0, !product.isEmpty()))");
        int shrink = discharge.indexOf("taken.shrink(1)");
        int give = discharge.indexOf("inventory.add(product.copy())");
        int drop = discharge.indexOf("player.drop(product.copy(), false)");
        int commit = discharge.indexOf("commit(player, found, plan.next())");
        int sync = discharge.indexOf("BackpackNetwork.sendFullSync(player, found.ref())");
        assertTrue(find > 0 && pick > find && product > pick && plan > product && shrink > plan && give > shrink && drop > give && commit > drop && sync > commit,
                "find, pick, product, decide, use up the container, give the product (or drop it), keep the liquid, sync the panel");
        assertTrue(discharge.contains("fromBackpack"), "the panel is only resynced when the container came from the backpack");
        assertTrue(actions.contains("pipe.emptyContainer() == PipeKind.Container.GLASS_BOTTLE ? Items.GLASS_BOTTLE : Items.BUCKET"), "only the vanilla bucket and bottle");
    }

    @Test
    void theProductsAreTheVanillaFilledContainersAndAnOilsOwnBucket() throws IOException {
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        String product = actions.substring(actions.indexOf("private static ItemStack productOf("));
        for (String item : new String[] {"Items.WATER_BUCKET", "Items.LAVA_BUCKET", "Items.MILK_BUCKET", "Items.HONEY_BOTTLE"}) {
            assertTrue(product.contains("new ItemStack(" + item + ")"), item);
        }
        assertTrue(product.contains("fluid.getBucket()") && product.contains("bucket == Items.AIR ? ItemStack.EMPTY"), "an oil with no bucket makes nothing");
    }

    // ---- 050: milk from a cow or goat, honey from a beehive ----

    @Test
    void theMilkPipeMilksAdultCowsAndGoatsOnTheServer() throws IOException {
        String item = read(SRC.resolve("item/PipeItem.java"));
        String milk = item.substring(item.indexOf("public InteractionResult interactLivingEntity("), item.indexOf("public InteractionResult useOn("));
        assertTrue(milk.contains("kind != PipeKind.MILK") && milk.contains("player.isShiftKeyDown()"), "only the milk pipe, and not while sneaking");
        assertTrue(milk.contains("target instanceof Cow || target instanceof Goat") && milk.contains("target.isBaby()"), "adult cows (mooshrooms are cows) and goats");
        int client = milk.indexOf("sidedSuccess(true)");
        int server = milk.indexOf("PipeActions.absorbEntity(serverPlayer, kind, target)");
        assertTrue(client > 0 && server > client, "the client only says it worked (the arm swings); the server does it");
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        String absorb = actions.substring(actions.indexOf("public static Result absorbEntity("), actions.indexOf("public static Result absorbHive("));
        int plan = absorb.indexOf("PipePlanner.absorb(pipe, situation(pipe, found, \"\"))");
        int commit = absorb.indexOf("commit(player, found, plan.next())");
        assertTrue(plan > 0 && commit > plan);
        assertTrue(absorb.contains("SoundEvents.COW_MILK") && absorb.contains("goat.isScreamingGoat()")
                && absorb.contains("SoundEvents.GOAT_SCREAMING_MILK") && absorb.contains("SoundEvents.GOAT_MILK"), "the sounds of milking");
    }

    @Test
    void theHoneyPipeTakesHoneyFromAReadyHiveTheWayABottleDoes() throws IOException {
        String item = read(SRC.resolve("item/PipeItem.java"));
        String useOn = item.substring(item.indexOf("public InteractionResult useOn("), item.indexOf("public InteractionResultHolder<ItemStack> use("));
        assertTrue(useOn.contains("kind != PipeKind.HONEY") && useOn.contains("player.isShiftKeyDown()"));
        assertTrue(useOn.contains("state.getBlock() instanceof BeehiveBlock") && useOn.contains("PipeActions.READY_HONEY_LEVEL"));
        int may = useOn.indexOf("level.mayInteract(player, pos) || !player.mayUseItemAt(");
        int absorb = useOn.indexOf("PipeActions.absorbHive(serverPlayer, kind, pos, state)");
        assertTrue(may > 0 && absorb > may);
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        assertTrue(actions.contains("READY_HONEY_LEVEL = 5"));
        String hive = actions.substring(actions.indexOf("public static Result absorbHive("), actions.indexOf("private static void angerNearbyBees("));
        int plan = hive.indexOf("PipePlanner.absorb(pipe, situation(pipe, found, \"\"))");
        int sound = hive.indexOf("SoundEvents.BOTTLE_FILL");
        int smoke = hive.indexOf("CampfireBlock.isSmokeyPos(level, pos)");
        int anger = hive.indexOf("angerNearbyBees(level, pos)");
        int release = hive.indexOf("hive.releaseBeesAndResetHoneyLevel(level, state, pos, player, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY)");
        int reset = hive.indexOf("hive.resetHoneyLevel(level, state, pos)");
        int emptied = hive.indexOf("getValue(BeehiveBlock.HONEY_LEVEL) >= READY_HONEY_LEVEL");
        int commit = hive.indexOf("commit(player, found, plan.next())");
        assertTrue(plan > 0 && sound > plan && smoke > sound && anger > smoke && release > anger && reset > release && emptied > reset && commit > emptied,
                "plan, the sound, the smoke, the bees, the hive, did it empty, keep the honey");
        assertTrue(hive.contains("GameEvent.FLUID_PICKUP"));
        String anger2 = actions.substring(actions.indexOf("private static void angerNearbyBees("));
        assertTrue(anger2.contains("inflate(8.0, 6.0, 8.0)") && anger2.contains("bee.getTarget() == null"), "vanilla's own rule for the bees");
    }

    @Test
    void theLeftButtonWithAMilkOrHoneyPipeNeverMinesAndOnlySaysThereIsNothingToPour() throws IOException {
        String actions = read(SRC.resolve("pipe/PipeActions.java"));
        String place = actions.substring(actions.indexOf("public static Result place("));
        int block = place.indexOf("!pipe.hasBlockForm()");
        int notice = place.indexOf("PipeNotices.send(player, Notice.NO_BLOCK_FORM, liquidName(pipe))");
        int find = place.indexOf("Found found = find(player)");
        assertTrue(block > 0 && notice > block && find > notice, "said before the backpack is even looked for");
        assertTrue(read(SRC.resolve("pipe/PipeEvents.java")).contains("event.setCanceled(true)"), "never mines (049)");
    }

    @Test
    void thereAreStillNoNewPacketsAndTheProtocolIsStill19() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("versioned(\"19\")"));
        assertFalse(network.contains("Pipe"));
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 3;")); // 051 raised it from 2
    }

    // ---- what was not touched ----

    @Test
    void nothingNewWasAddedToTheNetworkTheDataOrTheBuildOutsideTheDevRuntime() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"19\")"), "the protocol is still 19");
        assertFalse(network.contains("Pipe"), "no pipe packet");
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 3;"), "the data revision is 3 since 051 (049 itself left it at 2)");
        String gradle = read(Path.of("build.gradle"));
        assertTrue(gradle.contains("localRuntime \"maven.modrinth:pneumaticcraft-repressurized:8.2.24\""));
        assertFalse(gradle.contains("compileOnly \"maven.modrinth:pneumaticcraft"), "the pipes only know the oil by its tag");
        assertFalse(read(Path.of("src/main/templates/META-INF/neoforge.mods.toml")).contains("pneumaticcraft"));
    }

    @Test
    void noMainCodeNamesAnotherModsClassesAndThePureLogicNeedsNoMinecraft() throws IOException {
        try (var stream = Files.walk(SRC)) {
            for (Path path : (Iterable<Path>) stream.filter(p -> p.toString().endsWith(".java"))::iterator) {
                assertFalse(read(path).contains("me.desht.pneumaticcraft"), path.toString());
            }
        }
        for (String name : new String[] {"PipeKind", "Cooldown", "PipePlanner"}) {
            String source = read(SRC.resolve("pipe/" + name + ".java"));
            assertFalse(source.contains("import net.minecraft"), name);
            assertFalse(source.contains("import net.neoforged"), name);
        }
        assertEquals(1, read(SRC.resolve("pipe/PipeActions.java")).split("TagKey.create", -1).length - 1, "one tag: the crude oil");
    }
}
