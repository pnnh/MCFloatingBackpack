package pnnh.mcfloatingbackpack.inventory.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickViewportTest {
    @Test
    void iconsAreTwiceVanillaSizeWithAGapBetweenCells() {
        assertEquals(32, QuickViewport.ITEM);
        assertEquals(40, QuickViewport.SLOT);
        assertTrue(QuickViewport.SLOT > QuickViewport.ITEM);
    }

    @Test
    void aLargeWindowShowsNineByFourAndATriangleBelow() {
        QuickViewport.Fit fit = QuickViewport.layout(400, 300, 54, 0);
        assertEquals(9, fit.columns());
        assertEquals(4, fit.rows());
        assertEquals(6, fit.totalRows());
        assertEquals(0, fit.scroll());
        assertFalse(fit.moreAbove());
        assertTrue(fit.moreBelow());
        assertEquals((400 - 9 * QuickViewport.SLOT) / 2, fit.originX());
        assertEquals((300 - 4 * QuickViewport.SLOT) / 2, fit.originY());
    }

    @Test
    void scrollingClampsAndTheTrianglesFollowTheDirectionLeft() {
        QuickViewport.Fit middle = QuickViewport.layout(400, 300, 54, 1);
        assertEquals(1, middle.scroll());
        assertTrue(middle.moreAbove());
        assertTrue(middle.moreBelow());

        QuickViewport.Fit end = QuickViewport.layout(400, 300, 54, 99);
        assertEquals(2, end.scroll());
        assertTrue(end.moreAbove());
        assertFalse(end.moreBelow());

        QuickViewport.Fit start = QuickViewport.layout(400, 300, 54, -3);
        assertEquals(0, start.scroll());
        assertFalse(start.moreAbove());
    }

    @Test
    void fewerItemsShrinkTheGrid() {
        QuickViewport.Fit three = QuickViewport.layout(400, 300, 3, 0);
        assertEquals(3, three.columns());
        assertEquals(1, three.rows());
        assertEquals(1, three.totalRows());
        assertFalse(three.moreAbove());
        assertFalse(three.moreBelow());

        QuickViewport.Fit ten = QuickViewport.layout(400, 300, 10, 0);
        assertEquals(9, ten.columns());
        assertEquals(2, ten.rows());
        assertFalse(ten.moreBelow());
    }

    @Test
    void aNarrowWindowDropsColumnsBeforeItScrollsSideways() {
        int width = 8 * QuickViewport.SLOT + 2 * QuickViewport.MARGIN;
        QuickViewport.Fit fit = QuickViewport.layout(width, 300, 54, 0);
        assertEquals(8, fit.columns());
        assertTrue(fit.totalRows() > fit.rows());
        assertTrue(fit.moreBelow());
    }

    @Test
    void aTinyWindowStaysOneCellAndOnScreen() {
        QuickViewport.Fit fit = QuickViewport.layout(40, 40, 54, 0);
        assertEquals(1, fit.columns());
        assertEquals(1, fit.rows());
        assertTrue(fit.originX() >= 0);
        assertTrue(fit.originY() >= 0);
        assertTrue(fit.originX() + QuickViewport.SLOT <= 40);
        assertTrue(fit.originY() + QuickViewport.SLOT <= 40);
        assertTrue(fit.moreBelow());
        assertFalse(fit.moreAbove());
    }

    @Test
    void theLastRowIsLeftAlignedAndItsEmptyPartIsAMiss() {
        QuickViewport.Fit fit = QuickViewport.layout(400, 300, 10, 0);
        assertEquals(0, QuickViewport.hit(fit, fit.originX() + 1, fit.originY() + 1));
        assertEquals(9, QuickViewport.hit(fit, fit.originX() + 1, fit.originY() + QuickViewport.SLOT + 1));
        assertEquals(-1, QuickViewport.hit(fit, fit.originX() + QuickViewport.SLOT + 1, fit.originY() + QuickViewport.SLOT + 1));
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
