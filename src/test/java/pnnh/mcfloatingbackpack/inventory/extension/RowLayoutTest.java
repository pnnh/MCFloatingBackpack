package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RowLayoutTest {
    private static final RowLayout NONE = new RowLayout(36, List.of());
    private static final RowLayout TWO = new RowLayout(36, List.of("food", "tool"));

    @Test
    void withoutRowsNothingIsGroupedOrDrawn() {
        assertFalse(NONE.hasRows());
        for (int cols = 1; cols <= 9; cols++) {
            for (int i = 0; i < 60; i++) {
                assertFalse(NONE.topEdge(i, cols));
                assertFalse(NONE.leftEdge(i, cols));
                assertFalse(NONE.isRowStart(i));
            }
        }
        assertEquals(RowLayout.MAIN, NONE.groupOf(35));
        assertEquals(RowLayout.NONE, NONE.groupOf(36));
    }

    @Test
    void groupsFollowTheIndexRanges() {
        assertEquals(RowLayout.MAIN, TWO.groupOf(0));
        assertEquals(RowLayout.MAIN, TWO.groupOf(35));
        assertEquals(0, TWO.groupOf(36));
        assertEquals(0, TWO.groupOf(44));
        assertEquals(1, TWO.groupOf(45));
        assertEquals(1, TWO.groupOf(53));
        assertEquals(RowLayout.NONE, TWO.groupOf(54));
        assertEquals(RowLayout.NONE, TWO.groupOf(-1));
        assertEquals("food", TWO.categoryOf(40));
        assertEquals("tool", TWO.categoryOf(45));
        assertNull(TWO.categoryOf(3));
        assertNull(TWO.categoryOf(54));
    }

    @Test
    void groupsNeverDecrease() {
        int last = RowLayout.MAIN;
        for (int i = 0; i < 54; i++) {
            int g = TWO.groupOf(i);
            assertTrue(g >= last, "index " + i);
            last = g;
        }
    }

    @Test
    void aRowStartsEveryNineCellsFromTheEndOfTheTierCapacity() {
        assertFalse(TWO.isRowStart(35));
        assertTrue(TWO.isRowStart(36));
        assertFalse(TWO.isRowStart(37));
        assertTrue(TWO.isRowStart(45));
        assertFalse(TWO.isRowStart(54));
    }

    @Test
    void theOrdinaryGridNeverGetsALine() {
        for (int cols = 1; cols <= 9; cols++) {
            for (int i = 0; i < 36; i++) {
                assertFalse(TWO.topEdge(i, cols), "cols " + cols + " index " + i);
                assertFalse(TWO.leftEdge(i, cols), "cols " + cols + " index " + i);
            }
            assertFalse(TWO.topEdge(54, cols));
            assertFalse(TWO.leftEdge(54, cols));
        }
    }

    @Test
    void nineColumnsWithABaseOfThirtySixGivesCleanHorizontalLines() {
        // 36 cells are four full lines of nine: the first row is line five, the second line six.
        for (int i = 36; i < 45; i++) {
            assertTrue(TWO.topEdge(i, 9), "top of " + i);
            assertFalse(TWO.leftEdge(i, 9), "left of " + i);
        }
        for (int i = 45; i < 54; i++) {
            assertTrue(TWO.topEdge(i, 9), "top of " + i);
            assertFalse(TWO.leftEdge(i, 9), "left of " + i);
        }
    }

    @Test
    void aWrappedRowGetsAStaircase() {
        // 6 columns: cell 36 is at the start of a line (36 % 6 == 0), cells 36..41 the line, 42..44 the start of the next.
        for (int i = 36; i < 42; i++) {
            assertTrue(TWO.topEdge(i, 6), "top of " + i);
        }
        // Line 7 begins with cells 42, 43, 44 (still the first row, with the first row's line above: same group, no top line)
        for (int i = 42; i < 45; i++) {
            assertFalse(TWO.topEdge(i, 6), "top of " + i);
        }
        // and the second row starts at cell 45, in the middle of that line: a vertical line at its left, and a top line over
        // 45..47 (the cells above, 39..41, are the first row) but not over 48..50 (the cells above, 42..44, are the first row too!)
        assertTrue(TWO.leftEdge(45, 6));
        assertTrue(TWO.topEdge(45, 6));
        assertTrue(TWO.topEdge(46, 6));
        assertTrue(TWO.topEdge(47, 6));
        // 48 is the start of a line (48 % 6 == 0); above it is 42, in the first row: another group, so a top line
        assertTrue(TWO.topEdge(48, 6));
        // 51 is under 45 (second row): same group, no top line
        assertFalse(TWO.topEdge(51, 6));
        assertFalse(TWO.leftEdge(46, 6));
    }

    @Test
    void aBaseThatDoesNotFillTheLastLineStartsTheRowMidLine() {
        // base 40, 9 columns: cell 40 is the fifth of the fifth line
        RowLayout layout = new RowLayout(40, List.of("ore"));
        assertTrue(layout.leftEdge(40, 9));
        assertTrue(layout.topEdge(40, 9));
        assertTrue(layout.topEdge(44, 9)); // above is 35, the ordinary grid
        assertTrue(layout.topEdge(45, 9)); // 45 is on line six, above it 36, ordinary grid
        assertFalse(layout.leftEdge(41, 9));
    }

    @Test
    void aSingleColumnPutsALineOnTheTopOfEveryRowStartOnly() {
        for (int i = 37; i < 45; i++) {
            assertFalse(TWO.topEdge(i, 1), "inside a row at 1 column, " + i);
        }
        assertTrue(TWO.topEdge(36, 1));
        assertTrue(TWO.topEdge(45, 1));
        assertFalse(TWO.leftEdge(36, 1));
    }

    @Test
    void aColumnCountBelowOneCountsAsOne() {
        assertEquals(TWO.topEdge(45, 1), TWO.topEdge(45, 0));
        assertEquals(TWO.leftEdge(45, 1), TWO.leftEdge(45, -3));
    }

    // ---- 039: a row of a category this version does not know ----

    @Test
    void knownCategoriesAreNeverUnknownRows() {
        RowLayout layout = new RowLayout(36, List.of("food", "general", "disc"));
        for (int i = 0; i < 36 + 27; i++) {
            assertFalse(layout.isUnknownRow(i), "index " + i);
        }
    }

    @Test
    void aCategoryThisVersionDoesNotKnowMakesItsCellsUnknown() {
        RowLayout layout = new RowLayout(36, List.of("food", "zzz", ""));
        assertFalse(layout.isUnknownRow(36)); // food
        assertTrue(layout.isUnknownRow(45)); // zzz
        assertTrue(layout.isUnknownRow(53));
        assertTrue(layout.isUnknownRow(54)); // the empty category
        assertFalse(layout.isUnknownRow(10)); // the tier capacity
        assertFalse(layout.isUnknownRow(63)); // past every row
        assertFalse(layout.isUnknownRow(-1));
    }
}

