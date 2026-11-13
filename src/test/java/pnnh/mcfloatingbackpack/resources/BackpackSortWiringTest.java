package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 047: the wiring no pure test can see because the classes need Minecraft, and what was not touched. */
class BackpackSortWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theRequestIsRegisteredAndTheProtocolIs20() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("versioned(\"20\")"));
        assertTrue(network.contains("BackpackSortPayload.TYPE, BackpackSortPayload.STREAM_CODEC, BackpackSortPayload::handleServer"));
        assertTrue(network.contains("registrar.playToServer(BackpackSortPayload.TYPE"), "a client-to-server request");
    }

    @Test
    void theServerChecksEverythingTheOtherBatchActionsCheckBeforeItChangesAnything() throws IOException {
        String payload = read(SRC.resolve("network/BackpackSortPayload.java"));
        String handler = payload.substring(payload.indexOf("public static void handleServer"));
        int resolve = handler.indexOf("BackpackRef.resolve(player)");
        int valid = handler.indexOf("stillValid(player)");
        int carried = handler.indexOf("getCarried().isEmpty()");
        int gate = handler.indexOf("BackpackGate.acquireForPanel(player, ref)");
        int cursor = handler.indexOf("backpack.getCursor().isEmpty()");
        int sort = handler.indexOf("backpack.sortMain()");
        int save = handler.indexOf("ref.saveInventory(player, backpack)");
        int sync = handler.indexOf("BackpackNetwork.sendFullSync(player, ref)");
        assertTrue(resolve > 0 && valid > resolve && carried > valid && gate > carried && cursor > gate && sort > cursor
                && save > sort && sync > save, "resolve, validate, nothing held, registry, panel cursor, sort, save, sync - in this order");
        assertTrue(handler.contains("sendSession(player, false, null)"), "the usual way out of a session that is gone");
    }

    @Test
    void anAbortedSortIsLoggedAndTheClientIsStillToldTheState() throws IOException {
        String payload = read(SRC.resolve("network/BackpackSortPayload.java"));
        assertTrue(payload.contains("LOGGER.warn("));
        // the save is only inside the success branch; the sync is after both
        int save = payload.indexOf("ref.saveInventory(player, backpack)");
        int warn = payload.indexOf("LOGGER.warn(");
        int sync = payload.indexOf("BackpackNetwork.sendFullSync(player, ref)");
        assertTrue(save < warn && warn < sync);
    }

    @Test
    void sortMainOnlyWritesTheMainCellsAndOnlyWhenTheSorterAnswered() throws IOException {
        String inventory = read(SRC.resolve("inventory/BackpackInventory.java"));
        int start = inventory.indexOf("public boolean sortMain()");
        String body = inventory.substring(start, inventory.indexOf("/** Replaces the rows", start));
        assertTrue(body.contains("BackpackSorter.sort("));
        assertTrue(body.indexOf("if (after == null)") < body.indexOf("items.setStackInSlot(slot, after.get(slot))"));
        for (String untouched : new String[] {"tools", "hiddenRows", "cursor", "tags =", "trash", "pickupBlacklist", "setSize", "applyRows"}) {
            assertFalse(body.contains(untouched), "sortMain does not touch " + untouched);
        }
    }

    @Test
    void thePanelSendsOnlyWithAnEmptyCursorAndNotWhileTheSearchBoxIsOpenAndTakesThePressInBetween() throws IOException {
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        int magnifier = input.indexOf("SearchInput.onPress(state, mouseX, mouseY)");
        int sort = input.indexOf("state.hitsSortButton(mouseX, mouseY)");
        int snapshot = input.indexOf("state.hitsSnapshotButton(mouseX, mouseY)");
        assertTrue(magnifier > 0 && sort > magnifier && snapshot > sort, "magnifier, sort button, snapshot button");
        assertTrue(input.contains("!SearchState.get().isOpen() && state.hitsSortButton(mouseX, mouseY)"));
        String send = input.substring(input.indexOf("private static void sortBackpack()"));
        assertTrue(send.indexOf("cursorStack(minecraft).isEmpty()") < send.indexOf("PacketDistributor.sendToServer(new BackpackSortPayload())"));
        assertTrue(send.contains("PanelSelection.get().clear()"));
        // no prediction: the client never rearranges anything itself
        assertFalse(send.contains("setStackInSlot"));
    }

    @Test
    void theButtonIsDrawnWithFillOnlyWhileTheSearchBoxIsClosed() throws IOException {
        String renderer = read(SRC.resolve("client/panel/FloatingPanelRenderer.java"));
        int start = renderer.indexOf("private static void drawSortButton");
        String body = renderer.substring(start, renderer.indexOf("/** Geometry anchored at the same corner", start));
        assertTrue(body.contains("if (SearchState.get().isOpen()) {\n            return;"));
        assertTrue(body.contains("graphics.fill("));
        assertFalse(body.contains("blit"), "no texture");
        assertTrue(body.contains(".sort.tooltip"));
        assertTrue(renderer.indexOf("drawSortButton(graphics, state, minecraft, mouseX, mouseY);") > renderer.indexOf("drawSnapshotButton(graphics, state, minecraft, mouseX, mouseY);"));
    }

    @Test
    void theTooltipIsInBothLanguages() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            Matcher m = Pattern.compile("\"panel.mcfloatingbackpack.sort.tooltip\"\\s*:\\s*\"([^\"]+)\"").matcher(read(LANG.resolve(file)));
            assertTrue(m.find(), file);
            assertFalse(m.group(1).isBlank(), file);
        }
    }

    @Test
    void theSorterHasNoMinecraftTypesAndNothingElseWasAddedToTheNetworkConfigOrDependencies() throws IOException {
        String sorter = read(SRC.resolve("inventory/BackpackSorter.java"));
        assertFalse(sorter.contains("net.minecraft"));
        assertFalse(sorter.contains("net.neoforged"));
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        // exactly one request was added in 047: the sort
        assertEquals(1, Pattern.compile("047").matcher(network).results().count());
        assertEquals(1, Pattern.compile("defineListAllowEmpty").matcher(read(SRC.resolve("compat/CompatConfig.java"))).results().count());
        assertFalse(read(Path.of("src/main/templates/META-INF/neoforge.mods.toml")).toLowerCase().contains("sort"));
    }
}
