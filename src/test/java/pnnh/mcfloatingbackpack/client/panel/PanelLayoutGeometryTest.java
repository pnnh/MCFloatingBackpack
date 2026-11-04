package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Hit;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;
import pnnh.mcfloatingbackpack.inventory.PanelArea;

class PanelLayoutGeometryTest {
    private static final int S = PanelLayout.SLOT;

    /** A panel at (100, 50): cols x rows main area; slot counts main/left/right/bottom; scroll offsets main/left/right/bottom. */
    private static PanelLayout layout(int cols, int rows, int main, int left, int right, int bottom,
            int sMain, int sLeft, int sRight, int sBottom) {
        return PanelLayout.of(100, 50, cols, rows, new int[] {main, left, right, bottom},
                new int[] {sMain, sLeft, sRight, sBottom});
    }

    private static PanelLayout leather(int cols, int rows) {
        return layout(cols, rows, 36, 4, 4, 11, 0, 0, 0, 0);
    }

    @Test
    void sizeFormulas() {
        assertEquals(2 * PanelLayout.PAD_X + (9 + 2) * S, PanelLayout.width(9));
        assertEquals(PanelLayout.PAD_TOP + (4 + 1) * S + PanelLayout.PAD_BOTTOM, PanelLayout.height(4));
        PanelLayout l = leather(9, 4);
        assertEquals(PanelLayout.width(9), l.width());
        assertEquals(PanelLayout.height(4), l.height());
    }

    @Test
    void theSmallestPanelIsFiveColumnsByFourRows() {
        PanelLayout l = leather(3, 3);
        assertEquals(104, l.width());
        assertEquals(96, l.height());
        assertEquals(5, (l.width() - 2 * PanelLayout.PAD_X) / S);
        assertEquals(4, (l.height() - PanelLayout.PAD_TOP - PanelLayout.PAD_BOTTOM) / S);
    }

    @Test
    void theFourAreasTileTheWholePanelWithoutGaps() {
        PanelLayout l = leather(5, 4);
        int x0 = 100 + PanelLayout.PAD_X;
        int y0 = 50 + PanelLayout.PAD_TOP;
        assertEquals(new Rect(x0 + S, y0, 5 * S, 4 * S), l.rect(PanelArea.MAIN));
        assertEquals(new Rect(x0, y0, S, 4 * S), l.rect(PanelArea.LEFT));
        assertEquals(new Rect(x0 + 6 * S, y0, S, 4 * S), l.rect(PanelArea.RIGHT));
        // The bottom row starts under the left column and ends under the right column: no empty corners.
        assertEquals(new Rect(x0, y0 + 4 * S, 7 * S, S), l.rect(PanelArea.BOTTOM));
        int covered = 0;
        for (PanelArea area : PanelArea.values()) {
            Rect r = l.rect(area);
            covered += r.w() * r.h();
        }
        assertEquals((5 + 2) * (4 + 1) * S * S, covered);
    }

    @Test
    void visibleCountsFollowTheMainArea() {
        PanelLayout l = leather(6, 5);
        assertEquals(30, l.visibleCount(PanelArea.MAIN));
        assertEquals(5, l.visibleCount(PanelArea.LEFT));
        assertEquals(5, l.visibleCount(PanelArea.RIGHT));
        assertEquals(8, l.visibleCount(PanelArea.BOTTOM));
    }

    @Test
    void hitInTheMainAreaUsesTheScrollRowAndTheColumns() {
        PanelLayout l = layout(9, 4, 108, 12, 12, 11, 2, 0, 0, 0);
        Rect main = l.rect(PanelArea.MAIN);
        Hit hit = l.hit(main.x() + 3 * S + 4, main.y() + 1 * S + 4);
        assertEquals(new Hit(PanelArea.MAIN, (1 + 2) * 9 + 3), hit);
    }

    @Test
    void hitInATallPanelAfterReflowStillMapsRowsToIndexes() {
        PanelLayout l = layout(5, 4, 36, 4, 4, 11, 1, 0, 0, 0);
        Rect main = l.rect(PanelArea.MAIN);
        assertEquals(new Hit(PanelArea.MAIN, (0 + 1) * 5 + 4), l.hit(main.x() + 4 * S + 1, main.y() + 1));
    }

    @Test
    void hitInTheToolAreasUsesTheirOwnScrollOffsets() {
        PanelLayout l = layout(5, 4, 108, 12, 12, 11, 0, 3, 5, 2); // bottom row shows 7 of its 11 slots
        Rect left = l.rect(PanelArea.LEFT);
        Rect right = l.rect(PanelArea.RIGHT);
        Rect bottom = l.rect(PanelArea.BOTTOM);
        assertEquals(new Hit(PanelArea.LEFT, 3 + 2), l.hit(left.x() + 5, left.y() + 2 * S + 5));
        assertEquals(new Hit(PanelArea.RIGHT, 5 + 0), l.hit(right.x() + 5, right.y() + 5));
        assertEquals(new Hit(PanelArea.BOTTOM, 2 + 6), l.hit(bottom.x() + 6 * S + 5, bottom.y() + 5));
    }

    @Test
    void theBottomRowReachesUnderTheSideColumns() {
        PanelLayout l = leather(9, 4);
        Rect bottom = l.rect(PanelArea.BOTTOM);
        assertEquals(new Hit(PanelArea.BOTTOM, 0), l.hit(bottom.x() + 2, bottom.y() + 2));
        assertEquals(new Hit(PanelArea.BOTTOM, 10), l.hit(bottom.x() + bottom.w() - 3, bottom.y() + 2));
    }

    @Test
    void pointsOutsideEveryAreaHitNothing() {
        PanelLayout l = leather(5, 4);
        assertNull(l.hit(100 + 1, 50 + 1)); // title bar region
        assertNull(l.hit(100 + PanelLayout.PAD_X - 1, 50 + PanelLayout.PAD_TOP + 2)); // left padding
        assertNull(l.hit(-5, -5));
        Rect bottom = l.rect(PanelArea.BOTTOM);
        assertNull(l.hit(bottom.x() + 2, bottom.y() + bottom.h() + 2)); // below the bottom row
    }

    @Test
    void fillerCellsBeyondTheCapacityAreNotHittable() {
        // A tall main area (8 rows, 5 columns) next to a 4-slot left column: rows 5..8 of the column are fillers.
        PanelLayout l = layout(5, 8, 36, 4, 4, 11, 0, 0, 0, 0);
        Rect left = l.rect(PanelArea.LEFT);
        assertNotNull(l.hit(left.x() + 2, left.y() + 3 * S + 2));
        assertNull(l.hit(left.x() + 2, left.y() + 4 * S + 2));
        assertEquals(PanelArea.LEFT, l.areaAt(left.x() + 2, left.y() + 6 * S));
        assertEquals(-1, l.slotIndex(PanelArea.LEFT, 4));
        assertEquals(3, l.slotIndex(PanelArea.LEFT, 3));
    }

    @Test
    void theLastPartialRowOfTheMainAreaIsFillerToo() {
        PanelLayout l = layout(5, 8, 36, 4, 4, 11, 0, 0, 0, 0); // 36 slots in 5 columns = 7 full rows + 1 slot
        Rect main = l.rect(PanelArea.MAIN);
        assertEquals(new Hit(PanelArea.MAIN, 35), l.hit(main.x() + 2, main.y() + 7 * S + 2));
        assertNull(l.hit(main.x() + 1 * S + 2, main.y() + 7 * S + 2));
    }

    @Test
    void maximumScrollPerArea() {
        PanelLayout l = layout(9, 4, 108, 12, 12, 11, 0, 0, 0, 0);
        assertEquals(12 - 4, l.maxScroll(PanelArea.MAIN)); // 108 / 9 = 12 rows, 4 visible
        assertEquals(12 - 4, l.maxScroll(PanelArea.LEFT));
        assertEquals(12 - 4, l.maxScroll(PanelArea.RIGHT));
        assertEquals(0, l.maxScroll(PanelArea.BOTTOM)); // 11 slots, 11 visible
        PanelLayout narrow = layout(3, 3, 108, 12, 12, 11, 0, 0, 0, 0);
        assertEquals(36 - 3, narrow.maxScroll(PanelArea.MAIN));
        assertEquals(11 - 5, narrow.maxScroll(PanelArea.BOTTOM));
    }

    @Test
    void arrowsFollowTheScrollOffsets() {
        PanelLayout atStart = layout(3, 3, 36, 4, 4, 11, 0, 0, 0, 0);
        assertFalse(atStart.canBack(PanelArea.MAIN));
        assertTrue(atStart.canForward(PanelArea.MAIN));
        assertFalse(atStart.canBack(PanelArea.BOTTOM));
        assertTrue(atStart.canForward(PanelArea.BOTTOM));

        PanelLayout middle = layout(3, 3, 36, 4, 4, 11, 4, 0, 0, 3);
        assertTrue(middle.canBack(PanelArea.MAIN));
        assertTrue(middle.canForward(PanelArea.MAIN));
        assertTrue(middle.canBack(PanelArea.BOTTOM));
        assertTrue(middle.canForward(PanelArea.BOTTOM));

        PanelLayout atEnd = layout(3, 3, 36, 4, 4, 11, 9, 1, 1, 6);
        assertTrue(atEnd.canBack(PanelArea.MAIN));
        assertFalse(atEnd.canForward(PanelArea.MAIN));
        assertTrue(atEnd.canBack(PanelArea.LEFT));
        assertFalse(atEnd.canForward(PanelArea.LEFT));
        assertFalse(atEnd.canForward(PanelArea.BOTTOM));
    }

    @Test
    void anAreaThatFitsHasNoArrows() {
        PanelLayout l = leather(9, 4);
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.BOTTOM}) {
            assertFalse(l.canBack(area));
            assertFalse(l.canForward(area));
        }
    }

    @Test
    void cellPositionsMatchTheRectangles() {
        PanelLayout l = leather(5, 4);
        Rect main = l.rect(PanelArea.MAIN);
        assertEquals(main.x() + 2 * S, l.cellX(PanelArea.MAIN, 1 * 5 + 2));
        assertEquals(main.y() + 1 * S, l.cellY(PanelArea.MAIN, 1 * 5 + 2));
        Rect bottom = l.rect(PanelArea.BOTTOM);
        assertEquals(bottom.x() + 6 * S, l.cellX(PanelArea.BOTTOM, 6));
        assertEquals(bottom.y(), l.cellY(PanelArea.BOTTOM, 6));
        Rect right = l.rect(PanelArea.RIGHT);
        assertEquals(right.x(), l.cellX(PanelArea.RIGHT, 2));
        assertEquals(right.y() + 2 * S, l.cellY(PanelArea.RIGHT, 2));
    }
}
