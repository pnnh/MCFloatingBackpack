package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 067: what a pure test cannot execute, read back from the sources and the resources: the key, the screen, the two payloads,
 * the revision, and the one-per-bottom-area rule.
 */
class QuickAccessWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path ROOT = Path.of("src/main/resources");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theProtocolGainedTheTwoPayloadsAndTheRevisionIsSeven() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"1\").optional()"));
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"25\")"));
        assertFalse(network.contains("versioned(\"24\")"));
        assertTrue(network.indexOf("QuickSwapPayload::handleServer") < network.indexOf("QuickSwapResultPayload::handleClient"));
        String revision = read(SRC.resolve("inventory/revision/DataRevision.java"));
        assertTrue(revision.contains("CURRENT = 8;"));
        assertTrue(revision.contains("7 added the quick access item"));
        assertTrue(revision.contains("8 added the iron, gold, diamond and netherite quick access items"));
    }

    @Test
    void theKeyOpensOnlyInTheGameViewAndTheScreenIsNotThePanelHost() throws IOException {
        String keys = read(SRC.resolve("client/FloatingBackpackKeys.java"));
        assertTrue(keys.contains("GLFW.GLFW_KEY_Z"));
        assertTrue(keys.contains("getTranslatedKeyMessage()"));
        assertTrue(keys.contains("KeyConflictContext.IN_GAME"));
        assertTrue(keys.contains("event.register(QUICK_ACCESS)"));
        String events = read(SRC.resolve("client/FloatingPanelEvents.java"));
        int at = events.indexOf("isQuickAccessKey");
        int end = events.indexOf("keySwapOffhand.consumeClick()", at);
        String block = events.substring(at, end);
        assertTrue(block.contains("GLFW.GLFW_RELEASE"));
        assertTrue(block.contains("GLFW.GLFW_PRESS"));
        assertTrue(block.contains("QuickAccessOpener.tryOpen"));
        assertTrue(block.contains("!FloatingPanelState.get().isVisible()"));
        assertFalse(block.contains("setCanceled"), "InputEvent.Key cannot be cancelled; the off-hand click is drained instead");
        String screen = read(SRC.resolve("client/quick/QuickAccessScreen.java"));
        assertTrue(screen.contains("extends Screen"));
        assertFalse(screen.contains("PanelHostScreen"));
        assertTrue(screen.contains("public boolean isPauseScreen()"));
        assertTrue(screen.contains("return false;"));
        assertFalse(screen.contains("setAccessible"));
        assertFalse(screen.contains("@Mixin"));
        assertTrue(screen.contains("new QuickSwapPayload"));
        assertTrue(screen.contains("scrollY > 0"));
        assertTrue(screen.contains("if (ok)"));
        assertTrue(screen.contains("keyReleased"));
        assertTrue(screen.contains("fit.item()"));
        assertTrue(screen.contains("fit.slot()"));
        assertTrue(screen.contains("drawBackdrop"));
        assertTrue(screen.contains("COLOR_PANEL"));
        assertFalse(read(SRC.resolve("inventory/quick/QuickViewport.java")).contains("totalRows <= 1"));
        assertTrue(events.contains("keySwapOffhand.consumeClick()"));
        String opener = read(SRC.resolve("client/quick/QuickAccessOpener.java"));
        assertTrue(opener.contains("need_backpack"));
        assertTrue(opener.contains("quick.in_hand"));
        assertTrue(opener.contains("revisionProtected"));
        assertTrue(opener.contains("quick.need_tool"));
        assertTrue(opener.contains("quick.none"));
        assertFalse(opener.contains("acquireForOpen"));
    }

    @Test
    void theServerRevalidatesAndDoesNotCloseTheGridItself() throws IOException {
        String payload = read(SRC.resolve("network/QuickSwapPayload.java"));
        assertTrue(payload.contains("containerMenu != player.inventoryMenu"));
        assertTrue(payload.contains("normalized(selected)"));
        assertTrue(payload.contains("BackpackGate.acquire"));
        assertFalse(payload.contains("acquireForOpen("));
        assertTrue(payload.contains("hasQuickAccess()"));
        assertTrue(payload.contains("QuickLists.candidates"));
        assertTrue(payload.contains("HandSwapSlots.destination"));
        assertTrue(payload.contains("saveInventory"));
        assertTrue(payload.contains("sessionOpenOn"));
        assertTrue(payload.contains("sendSlotUpdate"));
        assertTrue(payload.contains("NOTICE_TICKS = 20"));
        assertTrue(payload.contains("quick.need_tool"));
        assertTrue(payload.contains("quick.no_room"));
        assertFalse(payload.contains("quick.wrong_row"));
        assertTrue(payload.contains("quick.refused"));
        assertTrue(payload.contains("new QuickSwapResultPayload(ok)"));
        String place = read(SRC.resolve("inventory/quick/HandSwapSlots.java"));
        assertTrue(place.contains("canPlaceMain"));
        assertTrue(place.contains("mainTargetOrder"));
        String click = read(SRC.resolve("network/BackpackClickPayload.java"));
        assertTrue(click.contains("HandSwapSlots.destination"));
        assertTrue(click.contains("quick.no_room"));
    }

    @Test
    void theItemIsLimitedToOneInTheBottomAndListedAfterTheTagManager() throws IOException {
        String items = read(SRC.resolve("item/ModItems.java"));
        int tag = items.indexOf("output.accept(TAG_MANAGER.get());");
        int quick = items.indexOf("QUICK_ACCESS_ITEMS.values()");
        assertTrue(tag >= 0 && quick > tag);
        assertFalse(items.substring(tag + "output.accept(TAG_MANAGER.get());".length(), quick).contains("output.accept("));
        assertTrue(items.contains("quickAccess(BackpackTier"));
        String inventory = read(SRC.resolve("inventory/BackpackInventory.java"));
        assertTrue(inventory.contains("boolean hasQuickAccess()"));
        assertTrue(inventory.contains("QuickAccessItem.tierOf"));
        assertFalse(inventory.contains("QUICK_ACCESS.get()"));
        assertTrue(read(SRC.resolve("inventory/tags/PresetTags.java")).contains("\"quick\""));
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        assertTrue(input.contains("QuickAccessPlace"));
        assertTrue(input.contains("quick.wrong_tier"));
        assertTrue(input.contains("limit.quick_access"));
        assertFalse(input.contains("ModItems.QUICK_ACCESS.get()"));
        String click = read(SRC.resolve("network/BackpackClickPayload.java"));
        assertTrue(click.contains("QuickAccessPlace"));
        assertTrue(click.contains("quick.wrong_tier"));
        assertTrue(click.contains("limit.quick_access"));
    }

    @Test
    void theResourcesExistInBothLanguages() throws IOException {
        String recipe = read(ROOT.resolve("data/mcfloatingbackpack/recipe/quick_access.json")).replaceAll("\\s+", "");
        assertTrue(recipe.contains("\"pattern\":[\"RIR\",\"LCL\",\"RIR\"]"));
        assertTrue(recipe.contains("\"id\":\"mcfloatingbackpack:quick_access\""));
        String model = read(ROOT.resolve("assets/mcfloatingbackpack/models/item/quick_access.json")).replaceAll("\\s+", "");
        assertTrue(model.contains("\"parent\":\"minecraft:item/generated\""));
        assertTrue(model.contains("\"layer0\":\"mcfloatingbackpack:item/quick_access\""));
        byte[] png = Files.readAllBytes(ROOT.resolve("assets/mcfloatingbackpack/textures/item/quick_access.png"));
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        String trash = read(ROOT.resolve("data/mcfloatingbackpack/tags/item/never_trash.json"));
        for (String id : new String[] {
                "quick_access", "iron_quick_access", "gold_quick_access", "diamond_quick_access", "netherite_quick_access"}) {
            assertTrue(trash.contains("\"mcfloatingbackpack:" + id + "\""), id);
            String upgrade = id.equals("quick_access") ? null : "upgrade_" + id + ".json";
            if (upgrade != null) {
                String smithing = read(ROOT.resolve("data/mcfloatingbackpack/recipe").resolve(upgrade)).replaceAll("\\s+", "");
                assertTrue(smithing.contains("\"type\":\"minecraft:smithing_transform\""));
                assertTrue(smithing.contains("\"id\":\"mcfloatingbackpack:" + id + "\""));
            }
        }
        assertFalse(read(SRC.resolve("network/BackpackNetwork.java")).contains("quick_access_tier"));
        for (String lang : new String[] {"zh_cn.json", "en_us.json"}) {
            String text = read(ROOT.resolve("assets/mcfloatingbackpack/lang").resolve(lang));
            for (String key : new String[] {
                    "item.mcfloatingbackpack.quick_access",
                    "item.mcfloatingbackpack.quick_access.tooltip",
                    "item.mcfloatingbackpack.tag_manager.tooltip",
                    "item.mcfloatingbackpack.trash_can.tooltip",
                    "item.mcfloatingbackpack.auto_pickup.tooltip",
                    "item.mcfloatingbackpack.iron_quick_access",
                    "item.mcfloatingbackpack.gold_quick_access",
                    "item.mcfloatingbackpack.diamond_quick_access",
                    "item.mcfloatingbackpack.netherite_quick_access",
                    "key.mcfloatingbackpack.quick_access",
                    "tag.mcfloatingbackpack.preset.quick",
                    "message.mcfloatingbackpack.limit.quick_access",
                    "message.mcfloatingbackpack.quick.need_tool",
                    "message.mcfloatingbackpack.quick.wrong_tier",
                    "message.mcfloatingbackpack.quick.in_hand",
                    "message.mcfloatingbackpack.quick.none",
                    "message.mcfloatingbackpack.quick.wrong_row",
                    "message.mcfloatingbackpack.quick.no_room",
                    "message.mcfloatingbackpack.quick.refused"}) {
                assertTrue(text.contains("\"" + key + "\""), lang + " missing " + key);
            }
            assertFalse(text.contains("按住 Z"), lang);
            assertFalse(text.contains("Hold Z"), lang);
        }
        String item = read(SRC.resolve("item/QuickAccessItem.java"));
        assertTrue(item.contains("FloatingBackpackKeys.quickAccessKey()"));
    }
}
