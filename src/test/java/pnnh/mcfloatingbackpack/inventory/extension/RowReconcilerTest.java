package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The reconcile rules of contracts/rules.md section 1 (R1 to R12). Cells are plain strings, "" is empty. */
class RowReconcilerTest {
    private static Row<String> row(String category, String... filled) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            cells.add(i < filled.length ? filled[i] : "");
        }
        return new Row<>(category, cells);
    }

    private static RowReconciler.Result<String> run(RowSet<String> rows, Map<String, Integer> placed) {
        return RowReconciler.reconcile(rows, placed, String::isEmpty, () -> "", ExtensionCategories.MAX_ROWS);
    }

    private static Map<String, Integer> placed(Object... pairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    private static RowSet<String> rows(List<Row<String>> visible, List<Row<String>> hidden) {
        return new RowSet<>(visible, hidden);
    }

    @Test
    void r1FirstPackMakesAnEmptyVisibleRow() {
        RowReconciler.Result<String> r = run(RowSet.empty(), placed("food", 1));
        assertEquals(List.of("food"), r.rows().visibleCategories());
        assertTrue(r.rows().visible().get(0).cells().stream().allMatch(String::isEmpty));
        assertTrue(r.layoutChanged());
        assertTrue(r.refused().isEmpty());
    }

    @Test
    void r2NewRowGoesAfterTheExistingOnes() {
        RowReconciler.Result<String> r = run(rows(List.of(row("food", "bread")), List.of()), placed("food", 1, "tool", 1));
        assertEquals(List.of("food", "tool"), r.rows().visibleCategories());
        assertEquals("bread", r.rows().visible().get(0).cells().get(0));
    }

    @Test
    void r3TakingAwayHidesTheLastRowWhenNoneIsEmpty() {
        RowReconciler.Result<String> r = run(rows(List.of(row("food", "bread")), List.of()), placed());
        assertEquals(List.of(), r.rows().visibleCategories());
        assertEquals(1, r.rows().hidden().size());
        assertEquals("bread", r.rows().hidden().get(0).cells().get(0));
        assertTrue(r.layoutChanged());
    }

    @Test
    void r4TakingAwayDeletesAnEmptyRowFirst() {
        RowReconciler.Result<String> r =
                run(rows(List.of(row("food", "bread"), row("food")), List.of()), placed("food", 1));
        assertEquals(List.of("food"), r.rows().visibleCategories());
        assertEquals("bread", r.rows().visible().get(0).cells().get(0));
        assertEquals(0, r.rows().hidden().size());
    }

    @Test
    void r4DeletesTheLastEmptyRowWhenSeveralAreEmpty() {
        RowSet<String> start = rows(List.of(row("food"), row("tool"), row("food")), List.of());
        RowReconciler.Result<String> r = run(start, placed("food", 1, "tool", 1));
        assertEquals(List.of("food", "tool"), r.rows().visibleCategories());
    }

    @Test
    void r5HiddenRowComesBackAtTheEndOfTheVisibleRows() {
        RowSet<String> start = rows(List.of(row("tool")), List.of(row("food", "bread")));
        RowReconciler.Result<String> r = run(start, placed("food", 1, "tool", 1));
        assertEquals(List.of("tool", "food"), r.rows().visibleCategories());
        assertEquals("bread", r.rows().visible().get(1).cells().get(0));
        assertEquals(0, r.rows().hidden().size());
    }

    @Test
    void r6TheRowHiddenFirstComesBackFirst() {
        RowSet<String> start = rows(List.of(), List.of(row("food", "first"), row("tool", "x"), row("food", "second")));
        RowReconciler.Result<String> r = run(start, placed("food", 1));
        assertEquals("first", r.rows().visible().get(0).cells().get(0));
        assertEquals(List.of("tool", "food"), r.rows().hidden().stream().map(Row::category).toList());
    }

    @Test
    void r7NoRowIsAddedPastTheLimit() {
        List<Row<String>> visible = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            visible.add(row("food", "f" + i));
        }
        RowSet<String> start = rows(visible, List.of());
        RowReconciler.Result<String> r = run(start, placed("food", 35, "tool", 1));
        assertEquals(start, r.rows());
        assertEquals(List.of("tool"), r.refused());
        assertFalse(r.layoutChanged());
    }

    @Test
    void r8BringingAHiddenRowBackIsNotLimited() {
        List<Row<String>> visible = new ArrayList<>();
        for (int i = 0; i < 34; i++) {
            visible.add(row("food", "f" + i));
        }
        RowSet<String> start = rows(visible, List.of(row("tool", "axe")));
        RowReconciler.Result<String> r = run(start, placed("food", 34, "tool", 1));
        assertEquals(35, r.rows().visible().size());
        assertEquals("tool", r.rows().visible().get(34).category());
        assertTrue(r.refused().isEmpty());
    }

    @Test
    void r9PacksVanishingLosesNothing() {
        RowSet<String> start = rows(List.of(row("food", "bread"), row("tool"), row("ore", "iron")), List.of());
        RowReconciler.Result<String> r = run(start, placed());
        assertEquals(List.of(), r.rows().visibleCategories());
        assertEquals(List.of("food", "ore"), r.rows().hidden().stream().map(Row::category).toList());
    }

    @Test
    void r11UnknownCategoriesAreHiddenNotDropped() {
        RowSet<String> start = rows(List.of(row("mystery", "thing"), row("mystery")), List.of());
        RowReconciler.Result<String> r = run(start, placed());
        assertEquals(List.of(), r.rows().visibleCategories());
        assertEquals(1, r.rows().hidden().size());
        assertEquals("thing", r.rows().hidden().get(0).cells().get(0));
    }

    @Test
    void anUnknownPlacedCategoryGetsARowLikeAnyOther() {
        RowReconciler.Result<String> r = run(RowSet.empty(), placed("mystery", 2));
        assertEquals(List.of("mystery", "mystery"), r.rows().visibleCategories());
    }

    @Test
    void reconcilingTwiceChangesNothingTheSecondTime() {
        RowSet<String> start = rows(List.of(row("food", "bread"), row("tool")), List.of(row("ore", "iron")));
        RowReconciler.Result<String> first = run(start, placed("food", 1, "ore", 2));
        RowReconciler.Result<String> second = run(first.rows(), placed("food", 1, "ore", 2));
        assertEquals(first.rows(), second.rows());
        assertFalse(second.layoutChanged());
    }

    @Test
    void layoutChangedIgnoresRowsThatOnlyMoveBetweenHiddenAndGone() {
        RowSet<String> start = rows(List.of(), List.of(row("food", "bread")));
        RowReconciler.Result<String> r = run(start, placed());
        assertFalse(r.layoutChanged());
    }

    private static List<String> items(RowSet<String> set) {
        List<String> out = new ArrayList<>();
        for (Row<String> row : set.visible()) {
            row.cells().stream().filter(c -> !c.isEmpty()).forEach(out::add);
        }
        for (Row<String> row : set.hidden()) {
            row.cells().stream().filter(c -> !c.isEmpty()).forEach(out::add);
        }
        out.sort(String::compareTo);
        return out;
    }

    /** R12: whatever packs are placed and taken, no item is ever lost, and the rows stay within their invariants. */
    @Test
    void r12RandomPlacingAndTakingNeverLosesAnItem() {
        Random random = new Random(35);
        List<String> ids = new ArrayList<>(ExtensionCategories.IDS);
        ids.add("mystery");
        RowSet<String> rows = RowSet.empty();
        Map<String, Integer> placed = new HashMap<>();
        int serial = 0;
        List<String> expected = new ArrayList<>();
        for (int step = 0; step < 4000; step++) {
            String category = ids.get(random.nextInt(ids.size()));
            int roll = random.nextInt(10);
            if (roll < 4) {
                placed.merge(category, 1, Integer::sum);
            } else if (roll < 7) {
                placed.merge(category, -1, Integer::sum);
                if (placed.get(category) <= 0) {
                    placed.remove(category);
                }
            } else if (roll < 8) {
                placed.clear();
            } else if (!rows.visible().isEmpty()) {
                // put an item into a random cell of a random visible row
                int r = random.nextInt(rows.visible().size());
                int c = random.nextInt(9);
                List<Row<String>> visible = new ArrayList<>(rows.visible());
                List<String> cells = new ArrayList<>(visible.get(r).cells());
                if (cells.get(c).isEmpty()) {
                    String item = "i" + serial++;
                    cells.set(c, item);
                    expected.add(item);
                    visible.set(r, new Row<>(visible.get(r).category(), cells));
                    rows = new RowSet<>(visible, rows.hidden());
                }
            }
            rows = run(rows, placed).rows();
            expected.sort(String::compareTo);
            assertEquals(expected, items(rows), "step " + step);
            assertTrue(rows.total() <= ExtensionCategories.MAX_ROWS, "step " + step);
            for (Row<String> hidden : rows.hidden()) {
                assertFalse(hidden.cells().stream().allMatch(String::isEmpty), "empty hidden row at step " + step);
            }
            for (String id : ids) {
                int want = Math.max(0, placed.getOrDefault(id, 0));
                assertTrue(rows.visibleOf(id) <= want, "more visible rows than packs at step " + step);
            }
        }
    }

    // ---- 038: the universal category goes through the same rules (the reconciler is not told about areas or tiers) ----

    @Test
    void universalRowsComeAndGoLikeAnyOther() {
        RowReconciler.Result<String> one = run(RowSet.empty(), placed("general", 1));
        assertEquals(List.of("general"), one.rows().visibleCategories());
        RowReconciler.Result<String> two = run(one.rows(), placed("general", 2));
        assertEquals(List.of("general", "general"), two.rows().visibleCategories());
        RowReconciler.Result<String> none = run(two.rows(), placed());
        assertEquals(List.of(), none.rows().visibleCategories());
        assertTrue(none.rows().hidden().isEmpty()); // both were empty: deleted, not hidden
    }

    @Test
    void aUniversalRowWithItemsIsHiddenWhenItsPackStopsCounting() {
        // for instance the backpack was upgraded and the pack is of the old tier: the count goes to 0
        RowReconciler.Result<String> r = run(rows(List.of(row("general", "stone", "dirt")), List.of()), placed());
        assertEquals(List.of(), r.rows().visibleCategories());
        assertEquals(1, r.rows().hidden().size());
        assertEquals("stone", r.rows().hidden().get(0).cells().get(0));
        assertTrue(r.layoutChanged());
    }

    @Test
    void theHiddenUniversalRowComesBackWithItsItems() {
        RowSet<String> hidden = rows(List.of(), List.of(row("general", "stone", "dirt")));
        RowReconciler.Result<String> r = run(hidden, placed("general", 1));
        assertEquals(List.of("general"), r.rows().visibleCategories());
        assertEquals("stone", r.rows().visible().get(0).cells().get(0));
        assertTrue(r.rows().hidden().isEmpty());
    }

    @Test
    void universalAndCategoryRowsDoNotDisturbEachOther() {
        RowReconciler.Result<String> r = run(
                rows(List.of(row("food", "bread"), row("general", "stone")), List.of()), placed("food", 1, "general", 1));
        assertEquals(List.of("food", "general"), r.rows().visibleCategories());
        assertFalse(r.layoutChanged());
        RowReconciler.Result<String> takeUniversal = run(r.rows(), placed("food", 1));
        assertEquals(List.of("food"), takeUniversal.rows().visibleCategories());
        assertEquals("bread", takeUniversal.rows().visible().get(0).cells().get(0));
        assertEquals(1, takeUniversal.rows().hidden().size());
    }

    @Test
    void atTheMostRowsAUniversalPackAddsNoRowAndIsReported() {
        List<Row<String>> full = new ArrayList<>();
        for (int i = 0; i < ExtensionCategories.MAX_ROWS; i++) {
            full.add(row("food", "bread"));
        }
        RowReconciler.Result<String> r = run(rows(full, List.of()), placed("food", ExtensionCategories.MAX_ROWS, "general", 1));
        assertEquals(ExtensionCategories.MAX_ROWS, r.rows().visible().size());
        assertEquals(List.of("general"), r.refused());
    }

    @Test
    void theUniversalCategoryIsProcessedAfterTheTen() {
        // two new rows added in one reconcile come out in the fixed order: a category first, the universal one last
        RowReconciler.Result<String> r = run(RowSet.empty(), placed("general", 1, "disc", 1));
        assertEquals(List.of("disc", "general"), r.rows().visibleCategories());
    }
}

