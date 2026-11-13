package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 037: the language keys of the category extension rows' interface, and the wiring that no pure test can see (the client classes
 * need Minecraft): the panel's valid sizes follow the tier capacity, the click and the drag ask the same placement guess, and the
 * renderer uses the row layout, the style table and the hidden hint.
 */
class CategoryExtensionUiResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final List<String> KEYS = List.of(
            "panel.mcfloatingbackpack.row.tooltip",
            "panel.mcfloatingbackpack.row.denied",
            "panel.mcfloatingbackpack.hidden.title",
            "panel.mcfloatingbackpack.hidden.line",
            "panel.mcfloatingbackpack.hidden.hint");
    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Set<String> keys(String json) {
        Set<String> out = new TreeSet<>();
        Matcher m = KEY.matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void bothLanguagesHaveTheFiveKeys() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            Set<String> keys = keys(read(ROOT.resolve(file)));
            for (String key : KEYS) {
                assertTrue(keys.contains(key), file + " has " + key);
            }
        }
    }

    @Test
    void theTwoLanguageFilesHaveTheSameKeys() throws IOException {
        assertEquals(keys(read(ROOT.resolve("zh_cn.json"))), keys(read(ROOT.resolve("en_us.json"))));
    }

    @Test
    void theRowTooltipTakesTheCategoryNameByPosition() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            Matcher m = Pattern.compile("\"panel.mcfloatingbackpack.row.tooltip\"\\s*:\\s*\"([^\"]*)\"").matcher(read(ROOT.resolve(file)));
            assertTrue(m.find(), file);
            assertTrue(m.group(1).contains("%1$s"), file + ": " + m.group(1));
        }
    }

    @Test
    void theHiddenLineTakesTheNameAndTheCellCount() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            Matcher m = Pattern.compile("\"panel.mcfloatingbackpack.hidden.line\"\\s*:\\s*\"([^\"]*)\"").matcher(read(ROOT.resolve(file)));
            assertTrue(m.find(), file);
            assertEquals(2, m.group(1).split("%s", -1).length - 1, file + ": " + m.group(1));
        }
    }

    @Test
    void theValidSizesFollowTheTierCapacityAndAnExtensionRowDoesNotResetTheScroll() throws IOException {
        String view = read(SRC.resolve("client/panel/ClientBackpackView.java"));
        assertTrue(view.contains("FloatingPanelState.get().setMainCount(baseCapacity());"));
        assertFalse(view.contains("setMainCount(slots.size())"));
        assertTrue(view.contains("boolean sizeChanged = base != previousBase;"));
        assertFalse(view.contains("boolean sizeChanged = count != slots.size();"));
    }

    @Test
    void theClickAndTheDragAskTheSamePlacementGuess() throws IOException {
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        assertTrue(input.contains("refusedByRow(slotIndex)"));
        assertTrue(input.contains("ClientPlacement.canPlace("));
        String drag = read(SRC.resolve("client/panel/PanelDragInput.java"));
        assertTrue(drag.contains("PanelDragInput::accepts"));
        assertTrue(drag.contains("ClientPlacement.canPlace("));
    }

    @Test
    void theClientGuessRunsTheServersRule() throws IOException {
        String guess = read(SRC.resolve("client/panel/ClientPlacement.java"));
        assertTrue(guess.contains("ExtensionRules.canPlace("));
        assertTrue(guess.contains("new ItemCategoryMatcher(view.tags())"));
        // the server's own check delegates to the same list version
        String rules = read(SRC.resolve("inventory/extension/ExtensionRules.java"));
        assertTrue(rules.contains("return canPlace(rows.visibleCategories(), baseCapacity, slot, stack, matcher);"));
        assertTrue(rules.contains("return targetSegments(rows.visibleCategories(), baseCapacity, stack, matcher);"));
    }

    @Test
    void theRendererUsesTheRowLayoutTheStyleTableAndTheHiddenHint() throws IOException {
        String renderer = read(SRC.resolve("client/panel/FloatingPanelRenderer.java"));
        assertTrue(renderer.contains("RowLayout"));
        assertTrue(renderer.contains("CategoryStyle.fill("));
        assertTrue(renderer.contains("CategoryStyle.edge("));
        assertTrue(renderer.contains("HiddenHint.lines("));
        assertTrue(renderer.contains("TitleDotLayout"));
        assertTrue(renderer.contains("ClientPlacement.canPlace("));
    }

    @Test
    void thisFeatureAddsNoNetworkPacketAndKeepsTheProtocol() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("versioned(\"20\")"));
    }
}
