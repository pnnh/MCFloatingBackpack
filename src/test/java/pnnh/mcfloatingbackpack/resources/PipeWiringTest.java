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
        assertFalse(item.contains("useOn"), "the default (pass) lets vanilla go on to use(), as for a bucket");
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

    // ---- what was not touched ----

    @Test
    void nothingNewWasAddedToTheNetworkTheDataOrTheBuildOutsideTheDevRuntime() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"19\")"), "the protocol is still 19");
        assertFalse(network.contains("Pipe"), "no pipe packet");
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 2;"), "the data revision is still 2");
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
