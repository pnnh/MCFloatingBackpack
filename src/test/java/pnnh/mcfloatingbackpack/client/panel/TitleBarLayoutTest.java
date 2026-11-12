package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 042: the title bar's pieces at the widths of 3, 4 and 9 main columns (x relative to the panel's left edge). */
class TitleBarLayoutTest {
    private static TitleBarLayout at(int cols) {
        return TitleBarLayout.of(0, PanelLayout.width(cols), cols > 3);
    }

    @Test
    void threeColumnsHaveNoCloseButtonAndEverythingElseFits() {
        TitleBarLayout layout = at(3);
        assertEquals(104, PanelLayout.width(3));
        assertFalse(layout.hasClose());
        assertTrue(layout.hasSnapshot());
        assertEquals(91, layout.snapshotLeft());
        assertEquals(79, layout.searchLeft());
        assertEquals(79, layout.textRightLimit());
        assertEquals(23, layout.boxLeft());
        assertEquals(101, layout.boxRight());
    }

    @Test
    void fourColumnsKeepTheCloseButtonAndTheBoxStopsBeforeIt() {
        TitleBarLayout layout = at(4);
        assertEquals(122, PanelLayout.width(4));
        assertTrue(layout.hasClose());
        assertEquals(109, layout.closeLeft());
        assertEquals(97, layout.snapshotLeft());
        assertEquals(85, layout.searchLeft());
        assertEquals(23, layout.boxLeft());
        assertEquals(107, layout.boxRight());
    }

    @Test
    void nineColumns() {
        TitleBarLayout layout = at(9);
        assertEquals(212, PanelLayout.width(9));
        assertEquals(199, layout.closeLeft());
        assertEquals(187, layout.snapshotLeft());
        assertEquals(175, layout.searchLeft());
        assertEquals(197, layout.boxRight());
    }

    @Test
    void theNumberKeepsItsRoomAtEveryWidth() {
        for (int cols = 3; cols <= 9; cols++) {
            TitleBarLayout layout = at(cols);
            int textStart = TitleIdLayout.of(0, layout.textRightLimit(), 24).x();
            assertTrue(layout.textRightLimit() - TitleIdLayout.MARGIN - textStart >= TitleBarLayout.MIN_TEXT, "cols " + cols);
        }
    }

    @Test
    void nothingOverlapsOrLeavesThePanel() {
        for (int cols = 3; cols <= 9; cols++) {
            int width = PanelLayout.width(cols);
            TitleBarLayout layout = at(cols);
            int size = TitleBarLayout.BUTTON_SIZE;
            assertTrue(layout.searchLeft() + size <= layout.snapshotLeft() || !layout.hasSnapshot(), "search/snapshot " + cols);
            int rightmost = layout.hasClose() ? layout.closeLeft() : layout.snapshotLeft();
            assertTrue(layout.snapshotLeft() + size <= (layout.hasClose() ? layout.closeLeft() : width), "snapshot " + cols);
            assertTrue(layout.closeLeft() + size <= width - TitleBarLayout.RIGHT_MARGIN, "close " + cols);
            assertTrue(layout.boxRight() <= width - TitleBarLayout.RIGHT_MARGIN, "box " + cols);
            assertTrue(layout.boxLeft() < layout.boxRight(), "box width " + cols);
            assertTrue(rightmost > layout.boxLeft());
        }
    }

    @Test
    void aVeryNarrowBarDropsTheSnapshotButtonFirstAndKeepsTheMagnifier() {
        TitleBarLayout narrow = TitleBarLayout.of(0, 70, true);
        assertFalse(narrow.hasSnapshot());
        assertEquals(narrow.searchLeft(), narrow.textRightLimit());
        assertEquals(narrow.snapshotLeft(), narrow.searchLeft());
    }

    @Test
    void theLayoutFollowsThePanelsPosition() {
        TitleBarLayout at0 = TitleBarLayout.of(0, 212, true);
        TitleBarLayout at100 = TitleBarLayout.of(100, 212, true);
        assertEquals(at0.closeLeft() + 100, at100.closeLeft());
        assertEquals(at0.searchLeft() + 100, at100.searchLeft());
        assertEquals(at0.boxLeft() + 100, at100.boxLeft());
        assertEquals(at0.boxRight() + 100, at100.boxRight());
    }
}
