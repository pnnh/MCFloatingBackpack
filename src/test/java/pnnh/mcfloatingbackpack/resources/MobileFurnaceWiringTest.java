package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 055: what no pure test can see because the classes need Minecraft: that no packet was added (the request of 054 serves the furnace), what the
 * menu is and when it asks the server to write the state, how the state is kept on the item, the click, the two places that ask the placement rule,
 * the version, and that nothing reaches into anything it should not.
 */
class MobileFurnaceWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path TOOL = SRC.resolve("tool");
    private static final Path FURNACE = TOOL.resolve("furnace");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void noPacketWasAddedAndTheProtocolIsStillTwenty() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("versioned(\"22\")"));
        assertEquals(1, Pattern.compile("OpenToolPayload\\.TYPE").matcher(network).results().count());
        try (Stream<Path> files = Files.list(SRC.resolve("network"))) {
            assertFalse(files.anyMatch(f -> f.getFileName().toString().toLowerCase().contains("furnace")), "the furnace has no packet of its own");
        }
        assertTrue(read(TOOL.resolve("MobileTool.java")).contains("FURNACE((byte) 1)"));
    }

    @Test
    void theServerOpensTheFurnaceThroughTheStoreAndTheToolsClassStaysOutOfTheGate() throws IOException {
        String tools = read(TOOL.resolve("MobileTools.java"));
        assertTrue(tools.contains("case FURNACE -> ModItems.MOBILE_FURNACE.get();"));
        assertTrue(tools.contains("case FURNACE -> MobileFurnaceStore.provider(player);"));
        assertFalse(tools.contains("BackpackGate") || tools.contains("saveInventory") || tools.contains("setStack"));
    }

    @Test
    void theProviderGoesThroughTheGateRefusesAProtectedBackpackAndWorksTheFurnaceOutBeforeOpening() throws IOException {
        String store = read(FURNACE.resolve("MobileFurnaceStore.java"));
        int provider = store.indexOf("public static MenuProvider provider(");
        assertTrue(provider > 0);
        String body = store.substring(provider);
        assertTrue(body.contains("BackpackGate.acquire(player, ref).isEmpty()"));
        assertTrue(body.contains("BackpackGate.isProtected(ref.getStack(player))"));
        assertTrue(body.contains("message.mcfloatingbackpack.furnace.protected"));
        assertTrue(body.contains("player.serverLevel().getGameTime()"));
        assertTrue(body.indexOf("session.save()") < body.indexOf("new SimpleMenuProvider"));
        assertTrue(body.contains("Component.translatable(\"container.furnace\")"));
        assertFalse(store.contains("acquireForOpen"), "only an explicit open of the panel may confirm a protected backpack");
    }

    @Test
    void theStateIsWrittenOnlyIntoTheBackpackTheMenuWasOpenedOnAndOnlyThroughTheGate() throws IOException {
        String store = read(FURNACE.resolve("MobileFurnaceStore.java"));
        int write = store.indexOf("public static boolean write(");
        String body = store.substring(write, store.indexOf("public static MenuProvider provider("));
        assertTrue(body.contains("identity.equals(identityOf(ref.getStack(player)))"));
        assertTrue(body.contains("BackpackGate.acquire(player, ref)"));
        assertTrue(body.contains("ref.saveInventory(player, inventory)"));
        assertTrue(body.contains("ModDataComponents.MOBILE_FURNACE_STATE.get()"));
        assertTrue(body.contains("inventory.handler(PanelArea.RIGHT)"));
    }

    @Test
    void theMenuIsTheVanillaFurnaceMenuWithItsOwnValidityAndClock() throws IOException {
        String menu = read(FURNACE.resolve("MobileFurnaceMenu.java"));
        assertTrue(menu.contains("extends FurnaceMenu implements MobileMenu"));
        assertTrue(menu.contains("super(containerId, playerInventory, session.container(), session.data())"));
        assertFalse(menu.contains("super.stillValid"), "the vanilla check asks the container");
        assertTrue(menu.contains("session.identity().equals(MobileFurnaceStore.identityOf(backpack))"));
        assertTrue(menu.contains("MobileFurnaceStore.furnaceOf(backpack).isEmpty()"));
        int broadcast = menu.indexOf("public void broadcastChanges()");
        assertTrue(menu.substring(broadcast).indexOf("session.advanceTo(level.getGameTime())") < menu.substring(broadcast).indexOf("super.broadcastChanges()"));
        int removed = menu.indexOf("public void removed(Player player)");
        String removedBody = menu.substring(removed, menu.indexOf("super.removed(player)"));
        assertTrue(removedBody.contains("session.advanceTo(level.getGameTime())") && removedBody.contains("session.save()"));
        assertTrue(menu.contains("this.slots.set(2, result)"), "vanilla's output slot only pays experience for a block entity");
        assertTrue(menu.contains("session.payExperience(serverPlayer)"));
    }

    @Test
    void theStateIsWrittenWhenTheSlotsChangeNotOnATimer() throws IOException {
        String session = read(FURNACE.resolve("MobileFurnaceSession.java"));
        assertTrue(session.contains("new FurnaceStateContainer(core, rules, this::save)"));
        String container = read(FURNACE.resolve("FurnaceStateContainer.java"));
        assertTrue(container.contains("onChanged.run()"));
        int setItem = container.indexOf("public void setItem(");
        assertTrue(container.substring(setItem, container.indexOf("public boolean canPlaceItem(")).contains("setChanged()"));
        assertTrue(container.contains("core.cookProgress = 0"), "another kind of input starts the cooking over, as in vanilla");
        int pay = session.indexOf("public void payExperience(");
        String payBody = session.substring(pay);
        assertTrue(payBody.indexOf("core.recipesUsed.clear()") < payBody.indexOf("save()"), "the count is cleared and written, or the next take pays it twice");
        for (String banned : new String[] {"Timer", "ScheduledExecutor", "ServerTickEvent", "LevelTickEvent", "PlayerTickEvent"}) {
            assertFalse(session.contains(banned) || container.contains(banned), banned);
        }
    }

    @Test
    void theStateIsADataComponentOnTheItemAndTheRevisionIsSix() throws IOException {
        String components = read(SRC.resolve("inventory/ModDataComponents.java"));
        assertTrue(components.contains("\"mobile_furnace_state\""));
        assertTrue(components.contains("FurnaceState.CODEC") && components.contains("FurnaceState.STREAM_CODEC"));
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 6;"));
        assertTrue(read(SRC.resolve("inventory/BackpackContents.java")).contains("ItemStack.OPTIONAL_CODEC"), "the tool stacks carry their components in the backpack's own encoding");
    }

    @Test
    void aLeftClickOnTheToolInTheRightAreaAsksTheServerAndIsTaken() throws IOException {
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        int at = input.indexOf("ModItems.MOBILE_FURNACE.get())) {\n                    PacketDistributor.sendToServer(");
        assertTrue(at > 0);
        String before = input.substring(Math.max(0, at - 400), at);
        assertTrue(before.contains("!isRight"));
        assertTrue(before.contains("toolHit.area() == PanelArea.RIGHT"));
        assertTrue(before.contains("cursorStack(Minecraft.getInstance()).isEmpty()"));
        assertTrue(input.substring(at, at + 400).contains("MobileTool.FURNACE.id()"));
        assertTrue(input.substring(at, at + 400).contains("return true;"));
    }

    @Test
    void theTwoPlacesThatPutItemsIntoAToolAreaAskTheRightAreaRule() throws IOException {
        for (String file : new String[] {"network/BackpackClickPayload.java", "client/panel/FloatingPanelInput.java"}) {
            String source = read(SRC.resolve(file));
            assertTrue(source.contains("ModItems.MOBILE_FURNACE.get()"), file);
            assertTrue(source.contains("message.mcfloatingbackpack.limit.mobile_furnace"), file);
        }
    }

    @Test
    void theEngineHasNoMinecraftTypesAndNothingWritesIntoTheWorldOrReachesIntoAnything() throws IOException {
        for (String name : new String[] {"FurnaceCore", "FurnaceRules", "FurnaceEngine", "FurnaceExperience"}) {
            String source = read(FURNACE.resolve(name + ".java"));
            assertFalse(source.contains("net.minecraft"), name);
            assertFalse(source.contains("net.neoforged"), name);
        }
        try (Stream<Path> files = Files.walk(FURNACE)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = read(file);
                for (String banned : new String[] {"setAccessible", "getDeclaredField", "getDeclaredMethod", "Mixin", "AccessTransformer", "setBlock(",
                        "import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity", "AbstractFurnaceBlockEntity.serverTick("}) {
                    assertFalse(text.contains(banned), file + " has " + banned);
                }
            }
        }
        assertFalse(Files.exists(Path.of("src/main/resources/mcfloatingbackpack.mixins.json")));
    }
}
