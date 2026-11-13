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
 * 054: what no pure test can see because the classes need Minecraft: the request and where it is registered, what the server checks, the shape of
 * the menu, the click, the two places that ask the placement rule, and that nothing reaches into anything it should not.
 */
class MobileCraftingWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path TOOL = SRC.resolve("tool");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theRequestIsRegisteredToTheServerOnTheProtocol20() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"20\")"));
        assertTrue(network.contains("registrar.playToServer(OpenToolPayload.TYPE, OpenToolPayload.STREAM_CODEC, OpenToolPayload::handleServer)"));
    }

    @Test
    void theRequestCarriesOneByteAndTheServerBelievesNothingOfIt() throws IOException {
        String payload = read(SRC.resolve("network/OpenToolPayload.java"));
        assertTrue(payload.contains("record OpenToolPayload(byte tool)"), "only which tool, no item, no slot");
        assertTrue(payload.contains("MobileTool.fromByte(payload.tool())"));
        assertTrue(payload.contains("MobileTools.has(player, tool)"), "the server looks for itself");
        assertTrue(payload.contains("player.containerMenu instanceof MobileMenu"));
        assertTrue(payload.contains("ToolOpenPlanner.decide(tool, inRightArea, open)"));
        assertTrue(payload.contains("player.closeContainer()"));
        assertTrue(payload.contains("MobileTools.open(player, tool)"));
        assertTrue(payload.contains("context.player() instanceof ServerPlayer"));
    }

    @Test
    void theBackpackIsOnlyReadNotThroughTheGate() throws IOException {
        String tools = read(TOOL.resolve("MobileTools.java"));
        assertTrue(tools.contains("BackpackRef.resolve(player)"), "the backpack the B key opens");
        assertTrue(tools.contains("contents.tools().right()"), "only the right area counts");
        assertFalse(tools.contains("BackpackGate") || tools.contains("saveInventory") || tools.contains("setStack"));
    }

    @Test
    void theMenuIsTheVanillaCraftingMenuWithAPlaceAndItsOwnValidity() throws IOException {
        String menu = read(TOOL.resolve("MobileCraftingMenu.java"));
        assertTrue(menu.contains("extends CraftingMenu implements MobileMenu"));
        assertTrue(menu.contains("public boolean stillValid(Player player)"));
        assertTrue(menu.contains("MobileTools.has(player, MobileTool.CRAFTING_TABLE)"));
        assertFalse(menu.contains("super.stillValid"), "the vanilla check wants a crafting table block");
        String tools = read(TOOL.resolve("MobileTools.java"));
        assertTrue(tools.contains("ContainerLevelAccess.create(p.level(), p.blockPosition())"));
        assertFalse(tools.contains("ContainerLevelAccess.NULL") || menu.contains("ContainerLevelAccess.NULL)"), "NULL never works out the result");
        assertTrue(tools.contains("Component.translatable(\"container.crafting\")"));
        assertTrue(tools.contains("player.openMenu(provider)"));
    }

    @Test
    void aLeftClickOnTheToolInTheRightAreaAsksTheServerAndIsTaken() throws IOException {
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        int at = input.indexOf("ModItems.MOBILE_CRAFTING_TABLE.get())) {\n                    PacketDistributor.sendToServer(");
        assertTrue(at > 0);
        String before = input.substring(Math.max(0, at - 400), at);
        assertTrue(before.contains("!isRight"));
        assertTrue(before.contains("toolHit.area() == PanelArea.RIGHT"));
        assertTrue(before.contains("cursorStack(Minecraft.getInstance()).isEmpty()"));
        assertTrue(input.substring(at, at + 400).contains("MobileTool.CRAFTING_TABLE.id()"));
        assertTrue(input.substring(at, at + 400).contains("return true;"));
    }

    @Test
    void theTwoPlacesThatPutItemsIntoAToolAreaAskTheRightAreaRule() throws IOException {
        for (String file : new String[] {"network/BackpackClickPayload.java", "client/panel/FloatingPanelInput.java"}) {
            String source = read(SRC.resolve(file));
            assertTrue(source.contains("ModItems.MOBILE_CRAFTING_TABLE.get()"), file);
            assertTrue(source.contains("message.mcfloatingbackpack.limit.mobile_crafting_table"), file);
        }
    }

    @Test
    void nothingReachesIntoAnythingAndTheRevisionIsFive() throws IOException {
        try (Stream<Path> files = Files.walk(TOOL)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = read(file);
                for (String banned : new String[] {"setAccessible", "getDeclaredField", "getDeclaredMethod", "Mixin", "AccessTransformer"}) {
                    assertFalse(text.contains(banned), file + " has " + banned);
                }
            }
        }
        assertFalse(Files.exists(Path.of("src/main/resources/mcfloatingbackpack.mixins.json")));
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 5;"));
        assertEquals(1, Pattern.compile("defineListAllowEmpty").matcher(read(SRC.resolve("compat/CompatConfig.java"))).results().count());
    }

    @Test
    void thePureClassesHaveNoMinecraftTypes() throws IOException {
        for (String name : new String[] {"MobileTool", "ToolOpenPlanner", "MobileMenu"}) {
            String source = read(TOOL.resolve(name + ".java"));
            assertFalse(source.contains("net.minecraft"), name);
            assertFalse(source.contains("net.neoforged"), name);
        }
    }
}
