package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewLayout.Kind;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Rect;

/** 025: where everything on the overview list goes. Coordinates are relative to the main area. */
class TagOverviewLayoutTest {
    private static final int W = 160;
    private static final int H = 126;
    private static final int CLEAR_W = 40;

    private static TagOverviewLayout of(int rows, int scroll, boolean hasFilter, boolean editing, int dropdown) {
        return TagOverviewLayout.of(W, H, rows, scroll, hasFilter, editing, dropdown, CLEAR_W);
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    @Test
    void theMenuIconSitsInTheStatusRowAtTheRightEnd() {
        Rect icon = TagOverviewLayout.menuIconRect(W);
        assertEquals(14, icon.w());
        assertEquals(14, icon.h());
        assertEquals(2, icon.y());
        assertEquals(W - 2, icon.x() + icon.w());
        assertTrue(icon.y() + icon.h() <= TagPanelLayout.ROW);
    }

    @Test
    void rowsStartBelowTheFilterRowAndDoNotOverlap() {
        TagOverviewLayout layout = of(20, 0, false, false, 0);
        Rect filter = layout.filterRect();
        Rect first = layout.rowRect(0);
        assertTrue(first.y() >= filter.y() + filter.h());
        for (int i = 1; i < layout.visibleCount(); i++) {
            assertEquals(layout.rowRect(i - 1).y() + TagOverviewLayout.ROW_H, layout.rowRect(i).y());
        }
        for (int i = 0; i < layout.visibleCount(); i++) {
            assertTrue(layout.rowRect(i).y() + layout.rowRect(i).h() <= H);
        }
    }

    @Test
    void scrollIsClampedToWhatTheContentAllows() {
        TagOverviewLayout few = of(2, 5, false, false, 0);
        assertEquals(0, few.maxScroll());
        assertEquals(0, few.scroll());
        TagOverviewLayout many = of(50, 999, false, false, 0);
        assertEquals(50 - many.visibleRows(), many.maxScroll());
        assertEquals(many.maxScroll(), many.scroll());
        assertEquals(0, of(50, -4, false, false, 0).scroll());
    }

    @Test
    void theVisibleRowsAreTheOnesFromTheScroll() {
        TagOverviewLayout layout = of(50, 10, false, false, 0);
        assertEquals(10, layout.firstVisible());
        assertEquals(layout.visibleRows(), layout.visibleCount());
        TagOverviewLayout tail = of(12, 0, false, false, 0);
        assertEquals(Math.min(12, tail.visibleRows()), tail.visibleCount());
    }

    @Test
    void theControlsOfARowAreInsideItAndDoNotOverlap() {
        TagOverviewLayout layout = of(5, 0, false, false, 0);
        for (int i = 0; i < layout.visibleCount(); i++) {
            Rect row = layout.rowRect(i);
            Rect icon = layout.iconRect(i);
            Rect name = layout.nameRect(i);
            Rect count = layout.countRect(i);
            Rect clear = layout.clearRect(i);
            for (Rect r : new Rect[] {icon, name, count, clear}) {
                assertTrue(r.x() >= row.x() && r.x() + r.w() <= row.x() + row.w(), "inside the row");
                assertTrue(r.y() >= row.y() && r.y() + r.h() <= row.y() + row.h(), "inside the row");
            }
            assertFalse(overlaps(icon, name));
            assertFalse(overlaps(name, count));
            assertFalse(overlaps(count, clear));
            assertEquals(CLEAR_W, clear.w());
            assertEquals(row.x() + row.w(), clear.x() + clear.w());
        }
    }

    @Test
    void aNarrowPanelNeverGivesTheNameANegativeWidth() {
        TagOverviewLayout layout = TagOverviewLayout.of(60, H, 3, 0, false, false, 0, CLEAR_W);
        for (int i = 0; i < layout.visibleCount(); i++) {
            assertTrue(layout.nameRect(i).w() >= 0);
        }
    }

    @Test
    void hitsFindTheMenuTheFilterTheRowsAndTheirClearButtons() {
        TagOverviewLayout layout = of(5, 0, false, false, 0);
        Rect menu = TagOverviewLayout.menuIconRect(W);
        assertEquals(Kind.MENU, layout.hit(menu.x() + 1, menu.y() + 1).kind());
        Rect filter = layout.filterRect();
        assertEquals(Kind.FILTER, layout.hit(filter.x() + 3, filter.y() + 3).kind());
        Rect row = layout.rowRect(2);
        TagOverviewLayout.Hit rowHit = layout.hit(row.x() + 20, row.y() + 5);
        assertEquals(Kind.ROW, rowHit.kind());
        assertEquals(2, rowHit.index());
        Rect clear = layout.clearRect(2);
        TagOverviewLayout.Hit clearHit = layout.hit(clear.x() + 2, clear.y() + 2);
        assertEquals(Kind.CLEAR, clearHit.kind());
        assertEquals(2, clearHit.index());
        assertEquals(Kind.NONE, layout.hit(-1, 5).kind());
        assertEquals(Kind.NONE, layout.hit(W + 1, 5).kind());
    }

    @Test
    void rowIndicesInHitsAreAbsoluteWhenScrolled() {
        TagOverviewLayout layout = of(50, 10, false, false, 0);
        Rect row = layout.rowRect(0);
        assertEquals(10, layout.hit(row.x() + 20, row.y() + 5).index());
    }

    @Test
    void aFilterShowsAClearMarkAtTheRightEndOfTheFilterRow() {
        assertNull(of(3, 0, false, false, 0).filterClearRect());
        TagOverviewLayout layout = of(3, 0, true, false, 0);
        Rect clear = layout.filterClearRect();
        assertEquals(Kind.FILTER_CLEAR, layout.hit(clear.x() + 1, clear.y() + 1).kind());
        // the rest of the filter row is still the filter
        assertEquals(Kind.FILTER, layout.hit(layout.filterRect().x() + 2, layout.filterRect().y() + 2).kind());
    }

    @Test
    void whileEditingTheDropdownLiesOverTheRows() {
        TagOverviewLayout layout = of(20, 0, false, true, 3);
        assertEquals(3, layout.dropdownCount());
        Rect entry = layout.dropdownRect(1);
        TagOverviewLayout.Hit hit = layout.hit(entry.x() + 3, entry.y() + 3);
        assertEquals(Kind.DROPDOWN, hit.kind());
        assertEquals(1, hit.index());
        assertEquals(0, of(20, 0, false, false, 3).dropdownCount());
        assertEquals(TagOverviewLayout.DROPDOWN_MAX, of(20, 0, false, true, 99).dropdownCount());
    }

    @Test
    void noRowsMeansNothingToHitBelowTheFilter() {
        TagOverviewLayout layout = of(0, 0, false, false, 0);
        assertEquals(0, layout.visibleCount());
        assertEquals(Kind.NONE, layout.hit(30, 60).kind());
    }
}
