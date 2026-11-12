package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 046: the wiring no pure test can see because the classes need Minecraft, and that no input logic was touched. */
class GreyCellWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String renderer() throws IOException {
        return read(SRC.resolve("client/panel/FloatingPanelRenderer.java"));
    }

    @Test
    void aCellWithoutASlotIsDrawnGreyBeforeTheOldContinueAndNothingElseIsDrawnOnIt() throws IOException {
        String render = renderer();
        int branch = render.indexOf("if (index < 0) {");
        assertTrue(branch > 0);
        int end = render.indexOf("int sx = layout.cellX(area, ordinal);", branch);
        String grey = render.substring(branch, end);
        assertTrue(grey.contains("GreyCellRules.greyed(searching,"));
        assertTrue(grey.contains("TagPanelState.get().isOpen() || PickupPanelState.get().isOpen()"));
        assertTrue(grey.contains("drawSlotWell(graphics, layout.cellX(area, ordinal), layout.cellY(area, ordinal), COLOR_SLOT_GREY)"));
        assertTrue(grey.contains("continue;"));
        // no item, hit frame or row decoration on a grey cell
        assertFalse(grey.contains("renderItem"));
        assertFalse(grey.contains("drawHitFrame"));
        assertFalse(grey.contains("drawRowDecor"));
        assertTrue(render.contains("COLOR_SLOT_GREY = 0x"));
    }

    @Test
    void theGreyCellsAreDrawnBeforeTheHiddenSummaryWrittenOnThem() throws IOException {
        String render = renderer();
        assertTrue(render.indexOf("drawSlotsWithItems(graphics, mouseX, mouseY);") < render.indexOf("SearchRenderer.drawHiddenSummary("));
    }

    @Test
    void theSummaryIsAMaskOverTheWholeMainAreaFilledBeforeTheFirstLineIsDrawn() throws IOException {
        String renderer = read(SRC.resolve("client/panel/search/SearchRenderer.java"));
        int start = renderer.indexOf("public static void drawHiddenSummary");
        String body = renderer.substring(start, renderer.indexOf("/** A one pixel frame", start));
        int fill = body.indexOf("graphics.fill(area.x(), area.y(), areaRight, areaBottom, COLOR_SUMMARY_BACKING)");
        int text = body.indexOf("graphics.drawString(font, lines.get(i)");
        assertTrue(fill > 0 && text > fill, "the mask first, text after");
        // the whole area, with the same edges and colour as the tag panel (so the grey cells under it are not to be seen)
        assertTrue(body.contains("COLOR_MASK_EDGE_DARK") && body.contains("COLOR_MASK_EDGE_LIGHT"));
        assertTrue(renderer.contains("COLOR_SUMMARY_BACKING = 0xFFDCDCDC"));
        String tagPanel = read(SRC.resolve("client/panel/tags/TagPanelRenderer.java"));
        assertTrue(tagPanel.contains("COLOR_BACKGROUND = 0xFFDCDCDC"), "the tag panel's colour");
        // no early return between collecting the lines and the mask: an empty summary still masks the area
        assertFalse(body.substring(0, fill).contains("if (lines.isEmpty())"));
    }

    @Test
    void noInputLogicKnowsTheGreyCells() throws IOException {
        try (var files = Files.walk(SRC)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("GreyCellRules.java") || name.equals("FloatingPanelRenderer.java")) {
                    continue;
                }
                assertFalse(read(file).contains("GreyCellRules"), file + " does not know the grey cells: they cannot be hit at all");
            }
        }
    }

    @Test
    void theRulesHaveNoMinecraftTypesAndNothingWasAddedToTheNetworkOrTheConfig() throws IOException {
        String rules = read(SRC.resolve("client/panel/GreyCellRules.java"));
        assertFalse(rules.contains("net.minecraft"));
        assertFalse(rules.contains("net.neoforged"));
        assertTrue(read(SRC.resolve("network/BackpackNetwork.java")).contains("versioned(\"19\")"));
        assertEquals(1, Pattern.compile("defineListAllowEmpty").matcher(read(SRC.resolve("compat/CompatConfig.java"))).results().count());
    }
}
