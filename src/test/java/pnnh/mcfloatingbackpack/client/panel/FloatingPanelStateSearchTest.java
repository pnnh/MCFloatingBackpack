package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/** 042: how a search's slot list changes what the panel scrolls over and hits, and what it leaves alone. */
class FloatingPanelStateSearchTest {
    private final FloatingPanelState state = FloatingPanelState.get();

    @BeforeEach
    void setUp() {
        state.setVisible(true);
        state.setPos(FloatingPanelState.DEFAULT_POS_X, FloatingPanelState.DEFAULT_POS_Y);
        state.clearSizePreference();
        state.setMainCount(0);
        state.setTotalRows(12);
        state.setSearchSlots(null);
        state.setScrollRow(0);
    }

    @AfterEach
    void tearDown() {
        state.setSearchSlots(null);
        state.setScrollRow(0);
        state.setMainCount(0);
        state.setTotalRows(4);
    }

    private static int[] range(int count, int step) {
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = i * step;
        }
        return slots;
    }

    @Test
    void scrollingFollowsTheResultsAndTheBackpacksRowsAreLeftAlone() {
        state.layout(1000, 1000);
        int cols = state.getVisibleCols();
        int rows = state.getVisibleRows();
        state.setScrollRow(100);
        int plainMax = state.getScrollRow();
        assertEquals(12 - rows, plainMax);
        state.setSearchSlots(range(cols * 2, 2));
        // two rows of results fit the visible rows: nothing to scroll
        assertEquals(0, state.getScrollRow());
        assertEquals(12, state.getTotalRows());
        state.setScrollRow(100);
        assertEquals(Math.max(0, 2 - rows), state.getScrollRow());
        state.setSearchSlots(null);
        state.setScrollRow(100);
        assertEquals(plainMax, state.getScrollRow());
    }

    @Test
    void aLongResultListScrollsByItsOwnRows() {
        state.layout(1000, 1000);
        int cols = state.getVisibleCols();
        int rows = state.getVisibleRows();
        state.setSearchSlots(range(cols * (rows + 3), 1));
        state.setScrollRow(100);
        assertEquals(3, state.getScrollRow());
    }

    @Test
    void theLayoutModelShowsTheMappedSlots() {
        state.layout(1000, 1000);
        state.setSearchSlots(new int[] {7, 21, 30});
        PanelLayout layout = state.layoutModel(new int[] {54, 0, 0, 0});
        assertEquals(7, layout.slotIndex(PanelArea.MAIN, 0));
        assertEquals(21, layout.slotIndex(PanelArea.MAIN, 1));
        assertEquals(30, layout.slotIndex(PanelArea.MAIN, 2));
        assertEquals(-1, layout.slotIndex(PanelArea.MAIN, 3));
    }

    @Test
    void indexAtGivesTheBackpackSlotOfTheCellUnderTheMouse() {
        state.layout(1000, 1000);
        state.setSearchSlots(new int[] {7, 21, 30});
        PanelLayout.Rect main = state.layoutModel(new int[] {54, 0, 0, 0}).rect(PanelArea.MAIN);
        assertEquals(7, PanelSlots.indexAt(state, main.x() + 3, main.y() + 3));
        assertEquals(21, PanelSlots.indexAt(state, main.x() + PanelLayout.SLOT + 3, main.y() + 3));
        // the fourth cell has no slot behind it
        assertEquals(-1, PanelSlots.indexAt(state, main.x() + 3 * PanelLayout.SLOT + 3, main.y() + 3));
        state.setSearchSlots(null);
        assertEquals(0, PanelSlots.indexAt(state, main.x() + 3, main.y() + 3));
    }

    @Test
    void theClearedStateHasNoSearchList() {
        assertNull(state.getSearchSlots());
    }

    @Test
    void narrowingThePanelWhileSearchingKeepsTheListAndClampsTheScroll() {
        state.layout(1000, 1000);
        state.setSearchSlots(range(40, 1));
        state.setSizePreference(4, 9);
        state.layout(1000, 1000);
        state.setScrollRow(100);
        int atNine = state.getScrollRow();
        // 40 results over 9 columns: 5 rows, 4 shown
        assertEquals(1, atNine);
        state.setSizePreference(4, 3);
        state.layout(1000, 1000);
        // 40 results over 3 columns: 14 rows, 4 shown: the scroll stays legal and the list is kept
        assertEquals(40, state.getSearchSlots().length);
        state.setScrollRow(100);
        assertEquals(10, state.getScrollRow());
    }

    private void sizeTo(int cols) {
        state.setSizePreference(4, cols);
        state.layout(1000, 1000);
    }

    @Test
    void threeColumnsHaveNoCloseButtonToHit() {
        sizeTo(3);
        assertEquals(3, state.getVisibleCols());
        assertFalse(state.showClose());
        // where it used to be: 13 pixels in from the right edge, 2 below the top
        int x = state.getPosX() + state.getWidth() - 8;
        int y = state.getPosY() + 6;
        assertFalse(state.hitsCloseButton(x, y));
        // the snapshot button took the corner
        assertTrue(state.hitsSnapshotButton(x, y));
    }

    @Test
    void fourColumnsAndMoreKeepTheCloseButton() {
        for (int cols : new int[] {4, 6, 9}) {
            sizeTo(cols);
            assertTrue(state.showClose(), "cols " + cols);
            int x = state.getPosX() + state.getWidth() - 8;
            int y = state.getPosY() + 6;
            assertTrue(state.hitsCloseButton(x, y), "cols " + cols);
            assertFalse(state.hitsSnapshotButton(x, y), "cols " + cols);
        }
    }

    @Test
    void theMagnifierBoxAndClearButtonAreWhereTheLayoutSaysAndDoNotOverlapTheCloseButton() {
        for (int cols : new int[] {3, 4, 9}) {
            sizeTo(cols);
            int y = state.getPosY() + 6;
            TitleBarLayout bar = state.titleBar();
            assertTrue(state.hitsSearchButton(bar.searchLeft() + 3, y), "magnifier " + cols);
            assertTrue(state.hitsSearchBox(bar.boxLeft() + 3, y), "box " + cols);
            assertTrue(state.hitsSearchClear(bar.boxRight() - 4, y), "clear " + cols);
            assertFalse(state.hitsSearchBox(bar.boxRight() + 1, y), "box edge " + cols);
            assertFalse(state.hitsSearchBox(state.getPosX() + 10, y), "the tier icon is not the box " + cols);
            if (state.showClose()) {
                assertFalse(state.hitsSearchBox(state.closeButtonLeft() + 3, y));
            }
        }
    }
}
