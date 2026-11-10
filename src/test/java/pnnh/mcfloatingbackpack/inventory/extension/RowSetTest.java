package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RowSetTest {
    private static List<String> cells(String... filled) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            out.add(i < filled.length ? filled[i] : "");
        }
        return out;
    }

    private static Row<String> row(String category, String... filled) {
        return new Row<>(category, cells(filled));
    }

    private static RowSet<String> sanitize(List<Row<String>> v, List<Row<String>> h) {
        return RowSet.sanitize(v, h, String::isEmpty, () -> "");
    }

    @Test
    void constantsAreTheSpecifiedOnes() {
        assertEquals(List.of("food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc"),
                ExtensionCategories.IDS);
        assertEquals(9, ExtensionCategories.ROW_CELLS);
        assertEquals(35, ExtensionCategories.MAX_ROWS);
        assertEquals(423, ExtensionCategories.MAX_MAIN_SLOTS);
        assertTrue(ExtensionCategories.isKnown("crop"));
        assertFalse(ExtensionCategories.isKnown("plant"));
        assertFalse(ExtensionCategories.isKnown(null));
    }

    @Test
    void sanitizePadsAndCutsRowsToNineCells() {
        List<String> tooLong = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tooLong.add("x" + i);
        }
        RowSet<String> set = sanitize(List.of(new Row<>("food", List.of("a")), new Row<>("tool", tooLong)), List.of());
        assertEquals(9, set.visible().get(0).cells().size());
        assertEquals("a", set.visible().get(0).cells().get(0));
        assertEquals("", set.visible().get(0).cells().get(8));
        assertEquals(9, set.visible().get(1).cells().size());
        assertEquals("x8", set.visible().get(1).cells().get(8));
    }

    @Test
    void sanitizeDropsEmptyHiddenRowsButKeepsEmptyVisibleOnes() {
        RowSet<String> set = sanitize(List.of(row("food")), List.of(row("tool"), row("ore", "iron")));
        assertEquals(1, set.visible().size());
        assertEquals(1, set.hidden().size());
        assertEquals("ore", set.hidden().get(0).category());
    }

    @Test
    void sanitizeCutsToThirtyFiveRowsHiddenEndFirst() {
        List<Row<String>> visible = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            visible.add(row("food"));
        }
        List<Row<String>> hidden = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            hidden.add(row("tool", "t" + i));
        }
        RowSet<String> set = sanitize(visible, hidden);
        assertEquals(35, set.total());
        assertEquals(30, set.visible().size());
        assertEquals(5, set.hidden().size());
        assertEquals("t4", set.hidden().get(4).cells().get(0));

        RowSet<String> onlyVisible = sanitize(
                java.util.Collections.nCopies(40, row("food")), List.of());
        assertEquals(35, onlyVisible.visible().size());
    }

    @Test
    void sanitizeCutsLongCategoryIdsAndKeepsUnknownOnes() {
        RowSet<String> set = sanitize(List.of(row("a".repeat(30)), row("mystery")), List.of());
        assertEquals(16, set.visible().get(0).category().length());
        assertEquals("mystery", set.visible().get(1).category());
    }

    @Test
    void sanitizeToleratesNulls() {
        RowSet<String> set = RowSet.sanitize(null, null, String::isEmpty, () -> "");
        assertEquals(0, set.total());
    }

    @Test
    void countsAndCategories() {
        RowSet<String> set = new RowSet<>(List.of(row("food"), row("tool"), row("food")), List.of(row("food", "x")));
        assertEquals(2, set.visibleOf("food"));
        assertEquals(1, set.hiddenOf("food"));
        assertEquals(0, set.hiddenOf("tool"));
        assertEquals(4, set.total());
        assertEquals(27, set.visibleCells());
        assertEquals(List.of("food", "tool", "food"), set.visibleCategories());
    }

    @Test
    void hiddenSummaryCountsNonEmptyCellsInCategoryOrder() {
        RowSet<String> set = new RowSet<>(
                List.of(),
                List.of(row("zzz", "q"), row("tool", "a", "b"), row("food", "x"), row("tool", "c")));
        List<RowSet.HiddenSummary> summary = set.hiddenSummary(String::isEmpty);
        assertEquals(
                List.of(
                        new RowSet.HiddenSummary("food", 1),
                        new RowSet.HiddenSummary("tool", 3),
                        new RowSet.HiddenSummary("zzz", 1)),
                summary);
        assertTrue(set.hasHiddenItems(String::isEmpty));
        assertFalse(RowSet.<String>empty().hasHiddenItems(String::isEmpty));
        assertEquals(List.of(), RowSet.<String>empty().hiddenSummary(String::isEmpty));
    }
}
