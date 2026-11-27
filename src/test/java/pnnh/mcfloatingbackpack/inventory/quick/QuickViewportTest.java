package pnnh.mcfloatingbackpack.inventory.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickViewportTest {
    @Test
    void bothSizesKeepAnEightPixelGap() {
        assertEquals(32, QuickViewport.ITEM);
        assertEquals(40, QuickViewport.SLOT);
        assertEquals(24, QuickViewport.SMALL_ITEM);
        assertEquals(32, QuickViewport.SMALL_SLOT);
        assertEquals(8, QuickViewport.SLOT - QuickViewport.ITEM);
        assertEquals(8, QuickViewport.SMALL_SLOT - QuickViewport.SMALL_ITEM);
        assertEquals(6, QuickViewport.PREFERRED_ROWS);
    }

    @Test
    void aTallWindowShowsAllSixLargeRows() {
        QuickViewport.Fit fit = QuickViewport.layout(400, 300, 54, 0);
        assertEquals(32, fit.item());
        assertEquals(40, fit.slot());
        assertEquals(9, fit.columns());
        assertEquals(6, fit.rows());
        assertEquals(6, fit.totalRows());
        assertEquals(0, fit.scroll());
        assertFalse(fit.moreAbove());
        assertFalse(fit.moreBelow());
        assertEquals((400 - 9 * fit.slot()) / 2, fit.originX());
        assertEquals((300 - 6 * fit.slot()) / 2, fit.originY());
    }

    @Test
    void aNormalWindowUsesTheSmallerCellsAndStillShowsSixRows() {
        for (int height : new int[] {270, 240}) {
            QuickViewport.Fit fit = QuickViewport.layout(400, height, 54, 1);
            String label = Integer.toString(height);
            assertEquals(24, fit.item(), label);
            assertEquals(32, fit.slot(), label);
            assertEquals(9, fit.columns(), label);
            assertEquals(6, fit.rows(), label);
            assertEquals(0, fit.scroll(), label);
            assertFalse(fit.moreAbove(), label);
            assertFalse(fit.moreBelow(), label);
        }
    }

    @Test
    void aVeryShortWindowScrolls() {
        QuickViewport.Fit top = QuickViewport.layout(400, 220, 54, 0);
        assertEquals(24, top.item());
        assertEquals(32, top.slot());
        assertTrue(top.rows() < top.totalRows());
        assertTrue(top.moreBelow());
        assertFalse(top.moreAbove());

        QuickViewport.Fit end = QuickViewport.layout(400, 220, 54, 99);
        assertTrue(end.scroll() > 0);
        assertTrue(end.moreAbove());
        assertFalse(end.moreBelow());
    }

    @Test
    void aNarrowWindowDropsColumns() {
        QuickViewport.Fit fit = QuickViewport.layout(323, 240, 54, 0);
        assertTrue(fit.columns() < 9);
        assertTrue(fit.totalRows() > fit.rows());
        assertTrue(fit.moreBelow());
    }

    @Test
    void fewerItemsKeepAFullRowAndDoNotAddEmptyRows() {
        QuickViewport.Fit three = QuickViewport.layout(400, 300, 3, 0);
        assertEquals(9, three.columns());
        assertEquals(1, three.rows());
        assertEquals(1, three.totalRows());
        assertFalse(three.moreAbove());
        assertFalse(three.moreBelow());
        assertEquals(0, QuickViewport.hit(three, three.originX() + 1, three.originY() + 1));
        assertEquals(-1, QuickViewport.hit(three, three.originX() + 3 * three.slot() + 1, three.originY() + 1));

        QuickViewport.Fit ten = QuickViewport.layout(400, 300, 10, 0);
        assertEquals(9, ten.columns());
        assertEquals(2, ten.rows());
        assertFalse(ten.moreBelow());
    }

    @Test
    void aTinyWindowStaysOneCellAndOnScreen() {
        QuickViewport.Fit fit = QuickViewport.layout(40, 40, 54, 0);
        assertEquals(1, fit.columns());
        assertEquals(1, fit.rows());
        assertTrue(fit.originX() >= 0);
        assertTrue(fit.originY() >= 0);
        assertTrue(fit.originX() + fit.slot() <= 40);
        assertTrue(fit.originY() + fit.slot() <= 40);
        assertTrue(fit.moreBelow());
        assertFalse(fit.moreAbove());
    }

    @Test
    void theLastRowIsLeftAlignedAndItsEmptyPartIsAMiss() {
        QuickViewport.Fit fit = QuickViewport.layout(400, 300, 10, 0);
        assertEquals(0, QuickViewport.hit(fit, fit.originX() + 1, fit.originY() + 1));
        assertEquals(9, QuickViewport.hit(fit, fit.originX() + 1, fit.originY() + fit.slot() + 1));
        assertEquals(-1, QuickViewport.hit(fit, fit.originX() + fit.slot() + 1, fit.originY() + fit.slot() + 1));
        assertEquals(-1, QuickViewport.hit(fit, fit.originX() - 1, fit.originY() + 1));
        assertEquals(-1, QuickViewport.hit(null, 0, 0));
    }

    @Test
    void nothingToShowHasNoGrid() {
        QuickViewport.Fit fit = QuickViewport.layout(400, 300, 0, 2);
        assertEquals(0, fit.columns());
        assertEquals(0, fit.rows());
        assertEquals(-1, QuickViewport.hit(fit, 200, 150));
    }
}
