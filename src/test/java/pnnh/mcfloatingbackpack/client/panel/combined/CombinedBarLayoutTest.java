package pnnh.mcfloatingbackpack.client.panel.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.combined.CombinedBarLayout.Rect;

/** 065: where the combined bars are, which tab or arrow a point hits, and how the tab strip scrolls. */
class CombinedBarLayoutTest {
    private static final int X = 50;
    private static final int Y = 100;
    private static final int W = 200;

    private static CombinedBarLayout layout(int... tabWidths) {
        return CombinedBarLayout.of(X, Y, W, tabWidths);
    }

    @Test
    void theBarsHangAboveThePanelsTopEdgeAndEndAtIt() {
        CombinedBarLayout layout = layout(40);
        assertEquals(new Rect(X, Y - CombinedBarLayout.BARS_H, W, CombinedBarLayout.BARS_H), layout.barsRect());
        assertEquals(Y, layout.barsRect().y() + layout.barsRect().h());
        assertEquals(layout.tabBarRect(), layout.barsRect());
        assertEquals(new Rect(X, Y - CombinedBarLayout.TAB_H, W, CombinedBarLayout.TAB_H), layout.tabBarRect());
    }

    @Test
    void aPointOnThePanelItselfIsNotOnTheBars() {
        CombinedBarLayout layout = layout(40);
        assertTrue(layout.contains(X + 5, Y - 1));
        assertFalse(layout.contains(X + 5, Y));
        assertFalse(layout.contains(X - 1, Y - 5));
    }

    @Test
    void fewTabsDoNotOverflowAndHaveNoArrows() {
        CombinedBarLayout layout = layout(40, 40, 40);
        assertFalse(layout.overflows());
        assertEquals(0, layout.maxScroll());
        assertEquals(0, layout.hitArrow(layout.leftArrow().x() + 1, Y - 5));
    }

    @Test
    void tabsAreSideBySideWithAGap() {
        CombinedBarLayout layout = layout(40, 50);
        Rect first = layout.tabRect(0, 0);
        Rect second = layout.tabRect(1, 0);
        assertEquals(first.x() + first.w() + CombinedBarLayout.TAB_GAP, second.x());
        assertEquals(0, layout.hitTab(first.x() + 1, Y - 5, 0));
        assertEquals(1, layout.hitTab(second.x() + 1, Y - 5, 0));
        assertEquals(-1, layout.hitTab(first.x() + first.w() + 1, Y - 5, 0));
    }

    @Test
    void manyTabsOverflowAndTheStripShrinksToMakeRoomForArrows() {
        CombinedBarLayout layout = layout(60, 60, 60, 60);
        assertTrue(layout.overflows());
        assertTrue(layout.tabArea().w() < W);
        assertTrue(layout.maxScroll() > 0);
        assertEquals(-1, layout.hitArrow(layout.leftArrow().x() + 1, Y - 5));
        assertEquals(1, layout.hitArrow(layout.rightArrow().x() + 1, Y - 5));
    }

    @Test
    void aTabOutsideTheStripCannotBeHitAndOneAtTheEdgeIsClipped() {
        CombinedBarLayout layout = layout(60, 60, 60, 60);
        Rect last = layout.tabRect(3, 0);
        assertNull(layout.visibleTabRect(3, 0));
        assertEquals(-1, layout.hitTab(last.x() + 1, Y - 5, 0));
        int scroll = layout.maxScroll();
        assertNotNull(layout.visibleTabRect(3, scroll));
        assertEquals(3, layout.hitTab(layout.visibleTabRect(3, scroll).x() + 1, Y - 5, scroll));
        // the first tab is now partly or wholly scrolled off, and never reaches left of the strip
        Rect visible = layout.visibleTabRect(1, scroll);
        if (visible != null) {
            assertTrue(visible.x() >= layout.tabArea().x());
        }
    }

    @Test
    void scrollIsClampedBetweenZeroAndTheMaximum() {
        CombinedBarLayout layout = layout(60, 60, 60, 60);
        assertEquals(0, layout.clampScroll(-30));
        assertEquals(layout.maxScroll(), layout.clampScroll(10_000));
        assertEquals(5, layout.clampScroll(5));
    }

    @Test
    void scrollToShowBringsATabIntoTheStripWithTheLeastMovement() {
        CombinedBarLayout layout = layout(60, 60, 60, 60);
        int scroll = layout.scrollToShow(3, 0);
        Rect tab = layout.tabRect(3, scroll);
        assertTrue(tab.x() >= layout.tabArea().x());
        assertTrue(tab.x() + tab.w() <= layout.tabArea().x() + layout.tabArea().w());
        assertEquals(scroll, layout.scrollToShow(3, scroll));
        assertEquals(0, layout.scrollToShow(0, scroll));
    }

    @Test
    void noTabsIsFine() {
        CombinedBarLayout layout = layout();
        assertEquals(0, layout.tabCount());
        assertEquals(-1, layout.hitTab(X + 5, Y - 5, 0));
        assertFalse(layout.overflows());
    }
}
