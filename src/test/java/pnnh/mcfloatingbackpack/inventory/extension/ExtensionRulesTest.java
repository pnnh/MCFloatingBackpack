package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

class ExtensionRulesTest {
    /** Items are "category:name"; "trident" is both a tool and a weapon. */
    private static final CategoryMatcher<String> MATCHER = (category, stack) -> {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.equals("trident")) {
            return category.equals("tool") || category.equals("weapon");
        }
        return stack.startsWith(category + ":");
    };

    private static Row<String> row(String category) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            cells.add("");
        }
        return new Row<>(category, cells);
    }

    private static RowSet<String> rows(String... categories) {
        List<Row<String>> visible = new ArrayList<>();
        for (String category : categories) {
            visible.add(row(category));
        }
        return new RowSet<>(visible, List.of());
    }

    @Test
    void theTierCapacityTakesAnything() {
        RowSet<String> set = rows("food");
        assertTrue(ExtensionRules.canPlace(set, 36, 0, "tool:axe", MATCHER));
        assertTrue(ExtensionRules.canPlace(set, 36, 35, "cobblestone", MATCHER));
    }

    @Test
    void aRowTakesOnlyItsCategory() {
        RowSet<String> set = rows("food", "tool");
        assertTrue(ExtensionRules.canPlace(set, 36, 36, "food:bread", MATCHER));
        assertTrue(ExtensionRules.canPlace(set, 36, 44, "food:bread", MATCHER));
        assertFalse(ExtensionRules.canPlace(set, 36, 36, "cobblestone", MATCHER));
        assertFalse(ExtensionRules.canPlace(set, 36, 45, "food:bread", MATCHER));
        assertTrue(ExtensionRules.canPlace(set, 36, 45, "tool:axe", MATCHER));
    }

    @Test
    void slotsInNoVisibleRowTakeNothing() {
        RowSet<String> set = new RowSet<>(List.of(row("food")), List.of(row("tool")));
        assertFalse(ExtensionRules.canPlace(set, 36, 45, "tool:axe", MATCHER));
        assertFalse(ExtensionRules.canPlace(set, 36, 400, "food:bread", MATCHER));
        assertFalse(ExtensionRules.canPlace(set, 36, -1, "food:bread", MATCHER));
    }

    @Test
    void rowOfSlotMapsTheMainArea() {
        assertEquals(-1, ExtensionRules.rowOfSlot(36, 2, 0));
        assertEquals(-1, ExtensionRules.rowOfSlot(36, 2, 35));
        assertEquals(0, ExtensionRules.rowOfSlot(36, 2, 36));
        assertEquals(0, ExtensionRules.rowOfSlot(36, 2, 44));
        assertEquals(1, ExtensionRules.rowOfSlot(36, 2, 45));
        assertEquals(-2, ExtensionRules.rowOfSlot(36, 2, 54));
        assertEquals(-2, ExtensionRules.rowOfSlot(36, 0, 36));
        assertEquals(-2, ExtensionRules.rowOfSlot(36, 2, -1));
    }

    @Test
    void withoutRowsTheSegmentsAreTheOldOrder() {
        List<int[]> segments = ExtensionRules.targetSegments(RowSet.empty(), 4, "food:bread", MATCHER);
        assertEquals(2, segments.size());
        assertEquals(0, segments.get(0).length);
        assertArrayEquals(new int[] {0, 1, 2, 3}, segments.get(1));
    }

    @Test
    void withoutAMatchingRowTheFirstSegmentIsEmpty() {
        List<int[]> segments = ExtensionRules.targetSegments(rows("tool"), 4, "food:bread", MATCHER);
        assertEquals(0, segments.get(0).length);
        assertArrayEquals(new int[] {0, 1, 2, 3}, segments.get(1));
    }

    @Test
    void matchingRowsComeFirstInTheOrderOfTheRows() {
        List<int[]> segments = ExtensionRules.targetSegments(rows("food", "tool", "weapon"), 4, "trident", MATCHER);
        assertEquals(18, segments.get(0).length);
        assertEquals(4 + 9, segments.get(0)[0]);
        assertEquals(4 + 27 - 1, segments.get(0)[17]);
        assertEquals(4 + 18, segments.get(0)[9]);
        assertArrayEquals(new int[] {0, 1, 2, 3}, segments.get(1));
    }

    private static final QuickTransferLogic.StackOps<String> OPS = new QuickTransferLogic.StackOps<>() {
        @Override
        public String empty() {
            return "";
        }

        @Override
        public boolean isEmpty(String stack) {
            return stack.isEmpty();
        }

        @Override
        public int count(String stack) {
            return stack.isEmpty() ? 0 : Integer.parseInt(stack.substring(stack.lastIndexOf('*') + 1));
        }

        @Override
        public int maxStack(String stack) {
            return 64;
        }

        @Override
        public boolean sameKind(String a, String b) {
            return a.substring(0, a.lastIndexOf('*')).equals(b.substring(0, b.lastIndexOf('*')));
        }

        @Override
        public String withCount(String stack, int count) {
            return count <= 0 ? "" : stack.substring(0, stack.lastIndexOf('*')) + "*" + count;
        }
    };

    /** The two segments, tried with moveIntoSegments, give "matching row first, the rest into the tier capacity". */
    @Test
    void movingThroughTheSegmentsFillsTheMatchingRowThenTheMain() {
        RowSet<String> set = rows("food");
        String[] cells = new String[4 + 9];
        java.util.Arrays.fill(cells, "");
        cells[4] = "food:bread*60"; // first cell of the food row, nearly full
        cells[0] = "food:bread*10"; // a partial stack in the main area
        List<int[]> segments = ExtensionRules.targetSegments(set, 4, "food:bread", MATCHER);

        String rest = QuickTransferLogic.moveIntoSegments(
                OPS, "food:bread*100", segments, i -> cells[i], (stack, i) -> cells[i] = stack);
        assertEquals("", rest);
        assertEquals("food:bread*64", cells[4]); // topped up first
        assertEquals("food:bread*64", cells[5]); // then the empty cells of the row
        assertEquals("food:bread*10", cells[0]); // main area not touched: the row had room for everything
        assertEquals("food:bread*" + (100 - 4 - 64), cells[6]);
    }

    @Test
    void whatTheRowCannotTakeGoesOnToTheMainArea() {
        RowSet<String> set = rows("food");
        String[] cells = new String[4 + 9];
        java.util.Arrays.fill(cells, "");
        for (int i = 4; i < 13; i++) {
            cells[i] = "food:bread*64";
        }
        List<int[]> segments = ExtensionRules.targetSegments(set, 4, "food:bread", MATCHER);
        String rest = QuickTransferLogic.moveIntoSegments(
                OPS, "food:bread*10", segments, i -> cells[i], (stack, i) -> cells[i] = stack);
        assertEquals("", rest);
        assertEquals("food:bread*10", cells[0]);
    }

    private static Map<String, Integer> placed(Object... pairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void fitsAllowsUpToTheLimit() {
        List<Row<String>> visible = new ArrayList<>();
        for (int i = 0; i < 34; i++) {
            visible.add(row("food"));
        }
        RowSet<String> set = new RowSet<>(visible, List.of());
        assertTrue(ExtensionRules.fits(set, placed("food", 34, "tool", 1), 35));
        assertFalse(ExtensionRules.fits(set, placed("food", 34, "tool", 2), 35));
        assertTrue(ExtensionRules.fits(set, placed("food", 35), 35));
        assertFalse(ExtensionRules.fits(set, placed("food", 36), 35));
    }

    @Test
    void bringingAHiddenRowBackNeedsNoNewRow() {
        List<Row<String>> visible = new ArrayList<>();
        for (int i = 0; i < 34; i++) {
            visible.add(row("food"));
        }
        RowSet<String> set = new RowSet<>(visible, List.of(new Row<>("tool", row("tool").cells())));
        assertEquals(0, ExtensionRules.newRowsNeeded(set, placed("food", 34, "tool", 1)));
        assertTrue(ExtensionRules.fits(set, placed("food", 34, "tool", 1), 35));
        assertEquals(1, ExtensionRules.newRowsNeeded(set, placed("food", 34, "tool", 2)));
    }

    @Test
    void packsAlreadyPlacedCostNothingMore() {
        RowSet<String> set = rows("food", "food");
        assertEquals(0, ExtensionRules.newRowsNeeded(set, placed("food", 2)));
        assertEquals(1, ExtensionRules.newRowsNeeded(set, placed("food", 3)));
    }
}
