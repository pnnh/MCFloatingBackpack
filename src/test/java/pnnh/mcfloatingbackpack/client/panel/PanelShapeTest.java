package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.PanelShape.Counts;
import pnnh.mcfloatingbackpack.inventory.PanelArea;

class PanelShapeTest {
    private static final Counts LEATHER = new Counts(36, 4, 4, 11);
    private static final Counts IRON = new Counts(54, 6, 6, 11);
    private static final Counts GOLD = new Counts(72, 8, 8, 11);
    private static final Counts DIAMOND = new Counts(90, 10, 10, 11);
    private static final Counts NETHERITE = new Counts(108, 12, 12, 11);
    private static final Counts[] TIERS = {LEATHER, IRON, GOLD, DIAMOND, NETHERITE};

    @Test
    void validColumnsOfTheFiveTiers() {
        assertEquals(List.of(3, 4, 6, 9), PanelShape.validCols(LEATHER));
        assertEquals(List.of(3, 6, 9), PanelShape.validCols(IRON));
        assertEquals(List.of(3, 4, 6, 8, 9), PanelShape.validCols(GOLD));
        assertEquals(List.of(3, 5, 6, 9), PanelShape.validCols(DIAMOND));
        assertEquals(List.of(3, 4, 6, 9), PanelShape.validCols(NETHERITE));
    }

    @Test
    void theRowLimitIsTheSideColumnsCapacityNotTheMainAreasTotalRows() {
        assertEquals(4, PanelShape.maxRows(LEATHER, 3)); // 12 total rows, but the side columns have 4 slots
        assertEquals(4, PanelShape.maxRows(LEATHER, 9));
        assertEquals(6, PanelShape.maxRows(IRON, 6));
        assertEquals(12, PanelShape.maxRows(NETHERITE, 3));
    }

    @Test
    void rowBoundaries() {
        assertFalse(PanelShape.isValidRows(IRON, 6, 2));
        assertTrue(PanelShape.isValidRows(IRON, 6, 3));
        assertTrue(PanelShape.isValidRows(IRON, 6, 6));
        assertFalse(PanelShape.isValidRows(IRON, 6, 7));
    }

    @Test
    void columnBoundaries() {
        assertFalse(PanelShape.isValidCols(IRON, 7)); // 54 / 7 leaves a half row
        assertFalse(PanelShape.isValidCols(IRON, 8));
        assertTrue(PanelShape.isValidCols(IRON, 6));
        assertFalse(PanelShape.isValidCols(IRON, 2));
        assertFalse(PanelShape.isValidCols(IRON, 10));
    }

    @Test
    void snapColsTakesTheLargestValidNotAboveTheTarget() {
        assertEquals(6, PanelShape.snapCols(IRON, 8));
        assertEquals(6, PanelShape.snapCols(IRON, 7));
        assertEquals(6, PanelShape.snapCols(IRON, 6));
        assertEquals(3, PanelShape.snapCols(IRON, 5));
        assertEquals(3, PanelShape.snapCols(IRON, 4));
        assertEquals(9, PanelShape.snapCols(IRON, 10));
        assertEquals(3, PanelShape.snapCols(IRON, 1)); // below the smallest: the smallest
        assertEquals(8, PanelShape.snapCols(GOLD, 8));
    }

    @Test
    void snapRowsClampsIntoTheRowRange() {
        assertEquals(6, PanelShape.snapRows(IRON, 6, 8));
        assertEquals(3, PanelShape.snapRows(IRON, 6, 1));
        assertEquals(5, PanelShape.snapRows(IRON, 6, 5));
    }

    @Test
    void aSmallBottomRowLimitsTheColumns() {
        // 6 slots in the bottom row: only cols + 2 <= 6 fit, i.e. 3 and 4.
        assertEquals(List.of(3, 4), PanelShape.validCols(new Counts(36, 4, 4, 6)));
    }

    @Test
    void withoutAnyValidShapeThereIsNoRestriction() {
        Counts[] unrestricted = {
            new Counts(0, 0, 0, 0), // nothing reported yet
            new Counts(37, 5, 5, 11), // 37 is prime: no column divides it
            new Counts(36, 4, 4, 4), // the bottom row is too short for even three columns
            new Counts(36, 2, 2, 11), // the side columns are too short for three rows
        };
        for (Counts counts : unrestricted) {
            assertFalse(PanelShape.restricted(counts), counts.toString());
            assertTrue(PanelShape.isValidCols(counts, 7));
            assertTrue(PanelShape.isValidRows(counts, 7, 8));
            assertEquals(7, PanelShape.snapCols(counts, 7));
            assertEquals(3, PanelShape.snapCols(counts, 1));
            assertEquals(9, PanelShape.snapCols(counts, 12));
            assertEquals(8, PanelShape.snapRows(counts, 7, 8));
            assertEquals(3, PanelShape.snapRows(counts, 7, 1));
        }
    }

    @Test
    void theStandardTiersAreRestricted() {
        for (Counts tier : TIERS) {
            assertTrue(PanelShape.restricted(tier));
            assertTrue(PanelShape.isValid(tier, 3, 3)); // the smallest main area is always allowed
        }
    }

    @Test
    void snappingAlwaysGivesAValidShapeAndIsIdempotent() {
        Random random = new Random(42);
        for (int run = 0; run < 200; run++) {
            Counts counts = new Counts(
                    9 * (1 + random.nextInt(12)),
                    3 + random.nextInt(10),
                    3 + random.nextInt(10),
                    5 + random.nextInt(7));
            int targetCols = random.nextInt(14);
            int targetRows = random.nextInt(16);
            int cols = PanelShape.snapCols(counts, targetCols);
            int rows = PanelShape.snapRows(counts, cols, targetRows);
            if (PanelShape.restricted(counts)) {
                assertTrue(PanelShape.isValid(counts, cols, rows), counts + " " + cols + "x" + rows);
            }
            assertEquals(cols, PanelShape.snapCols(counts, cols));
            assertEquals(rows, PanelShape.snapRows(counts, cols, rows));
        }
    }

    // ---- no solid filler cell anywhere ----

    private static boolean noFillerCell(Counts counts, int cols, int rows) {
        int[] sizes = {counts.main(), counts.left(), counts.right(), counts.bottom()};
        PanelLayout probe = PanelLayout.of(0, 0, cols, rows, sizes, new int[4]);
        for (PanelArea area : PanelArea.values()) {
            for (int offset = 0; offset <= probe.maxScroll(area); offset++) {
                int[] scroll = new int[4];
                scroll[area.ordinal()] = offset;
                PanelLayout layout = PanelLayout.of(0, 0, cols, rows, sizes, scroll);
                for (int ordinal = 0; ordinal < layout.visibleCount(area); ordinal++) {
                    if (layout.slotIndex(area, ordinal) < 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Test
    void everyValidShapeHasNoFillerCellAtAnyScrollPosition() {
        for (Counts tier : TIERS) {
            int checked = 0;
            for (int cols : PanelShape.validCols(tier)) {
                for (int rows = 3; rows <= PanelShape.maxRows(tier, cols); rows++) {
                    assertTrue(noFillerCell(tier, cols, rows), tier + " " + cols + "x" + rows);
                    checked++;
                }
            }
            assertTrue(checked > 0);
        }
    }

    @Test
    void theCheckReallyCatchesTheScreenshotCase() {
        // Iron backpack at 7 columns x 8 rows: the shape in the user's screenshot.
        assertFalse(PanelShape.isValid(IRON, 7, 8));
        assertFalse(noFillerCell(IRON, 7, 8));
        // Eight rows in the side columns alone is enough (they only hold six slots).
        assertFalse(PanelShape.isValid(IRON, 6, 8));
        assertFalse(noFillerCell(IRON, 6, 8));
    }

    @Test
    void invalidShapesOfTheStandardTiersAllShowFillerCells() {
        for (Counts tier : TIERS) {
            for (int cols = 3; cols <= 9; cols++) {
                for (int rows = 3; rows <= 14; rows++) {
                    if (!PanelShape.isValid(tier, cols, rows)) {
                        assertFalse(noFillerCell(tier, cols, rows), tier + " " + cols + "x" + rows);
                    }
                }
            }
        }
    }
}
