package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanelLayoutTest {
    private FloatingPanelState state;

    /** Wide enough that column fit never binds, so these pre-007 cases behave exactly as before. */
    private static final int WIDE_SCREEN = 1000;

    @BeforeEach
    void setUp() {
        state = FloatingPanelState.get();
        state.setVisible(true);
        state.setPos(FloatingPanelState.DEFAULT_POS_X, FloatingPanelState.DEFAULT_POS_Y);
        state.setScrollRow(0);
        state.endDrag();
        state.endResize();
        state.clearSizePreference();
    }

    @Test
    void layoutCapsVisibleRowsToTheScreen() {
        state.setTotalRows(12);
        // 40 bottom margin on a 240-tall GUI → room for fewer than 12 rows.
        state.layout(WIDE_SCREEN, 240);

        assertTrue(state.getVisibleRows() < 12);
        assertEquals(PanelSlots.panelWidth(state.getVisibleCols()), state.getWidth());
        assertEquals(PanelSlots.panelHeight(state.getVisibleRows()), state.getHeight());
        assertTrue(
                state.getPosY() + state.getHeight()
                        <= 240 - FloatingPanelState.BOTTOM_HUD_MARGIN + 1);
    }

    @Test
    void visibleRowsDoNotGrowWhenDraggedTowardTheTop() {
        // Regression test: the panel must not opportunistically claim more screen height just
        // because dragging it up leaves more empty space below it.
        state.setTotalRows(12);
        state.setPos(FloatingPanelState.DEFAULT_POS_X, 150);
        state.layout(WIDE_SCREEN, 400);
        int rowsNearBottom = state.getVisibleRows();

        state.setPos(FloatingPanelState.DEFAULT_POS_X, 0);
        state.layout(WIDE_SCREEN, 400);
        int rowsNearTop = state.getVisibleRows();

        assertEquals(rowsNearBottom, rowsNearTop);
    }

    @Test
    void layoutFitsAllRowsWhenTallEnough() {
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(4, state.getVisibleRows());
        assertEquals(PanelSlots.panelWidth(PanelSlots.COLS), state.getWidth());
    }

    @Test
    void scrollByClampsToMax() {
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 240);
        int max = state.getTotalRows() - state.getVisibleRows();

        state.scrollBy(100);
        assertEquals(max, state.getScrollRow());
        state.scrollBy(-100);
        assertEquals(0, state.getScrollRow());
    }

    @Test
    void defaultLayoutUsesAllNineColumns() {
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS, state.getVisibleCols());
        assertFalse(state.isSizePreferenceSet());
    }

    @Test
    void resizingColumnsLeavesRowsUnchanged() {
        state.setTotalRows(6);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(-4 * PanelSlots.SLOT_SIZE, 0);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS - 4, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());
        assertTrue(state.isSizePreferenceSet());
    }

    @Test
    void resizingRowsLeavesColumnsUnchanged() {
        state.setTotalRows(6);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(0, -3 * PanelSlots.SLOT_SIZE);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS, state.getVisibleCols());
        assertEquals(3, state.getVisibleRows());
    }

    @Test
    void resizeCannotGoBelowMinimumInEitherDimension() {
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(-50 * PanelSlots.SLOT_SIZE, -50 * PanelSlots.SLOT_SIZE);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(FloatingPanelState.MIN_VISIBLE_CELLS, state.getVisibleCols());
        assertEquals(FloatingPanelState.MIN_VISIBLE_CELLS, state.getVisibleRows());
    }

    @Test
    void resizeCannotExceedScreenFitInEitherDimension() {
        state.setTotalRows(20);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(50 * PanelSlots.SLOT_SIZE, 50 * PanelSlots.SLOT_SIZE);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS, state.getVisibleCols());
        assertTrue(state.getVisibleRows() < 20);
    }

    @Test
    void beginResizeClearsDragging() {
        state.beginDrag(0, 0);
        assertTrue(state.isDragging());

        state.beginResize(0, 0);

        assertTrue(state.isResizing());
        assertFalse(state.isDragging());
    }

    @Test
    void resizedSizeSurvivesAnAutoFitLayoutOnAnotherScreen() {
        // Once the player has set a preference, later layout() calls must respect it (clamped),
        // not silently fall back to auto-fit.
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);
        state.beginResize(0, 0);
        state.updateResize(-2 * PanelSlots.SLOT_SIZE, -2 * PanelSlots.SLOT_SIZE);
        state.endResize();
        int cols = state.getVisibleCols();
        int rows = state.getVisibleRows();

        state.layout(WIDE_SCREEN, 400);

        assertEquals(cols, state.getVisibleCols());
        assertEquals(rows, state.getVisibleRows());
    }

    @Test
    void thePanelIsNeverSmallerThanFiveColumnsByFourRows() {
        // 016: main area minimum 3x3, plus one column on each side and one row at the bottom.
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(-50 * PanelSlots.SLOT_SIZE, -50 * PanelSlots.SLOT_SIZE);
        state.endResize();
        state.layout(WIDE_SCREEN, 400);

        assertEquals(5 * PanelSlots.SLOT_SIZE + 2 * PanelSlots.PAD_X, state.getWidth());
        assertEquals(4 * PanelSlots.SLOT_SIZE + PanelSlots.PAD_TOP + PanelSlots.PAD_BOTTOM, state.getHeight());
    }

    @Test
    void resizingOnlyChangesTheMainAreaWhileTheToolAreasKeepTheirThickness() {
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);
        int[] counts = {108, 12, 12, 11};
        int sideBefore = state.layoutModel(counts).rect(pnnh.mcfloatingbackpack.inventory.PanelArea.LEFT).w();
        int bottomBefore = state.layoutModel(counts).rect(pnnh.mcfloatingbackpack.inventory.PanelArea.BOTTOM).h();

        state.beginResize(0, 0);
        state.updateResize(-4 * PanelSlots.SLOT_SIZE, -2 * PanelSlots.SLOT_SIZE);
        state.endResize();
        state.layout(WIDE_SCREEN, 400);

        PanelLayout after = state.layoutModel(counts);
        assertEquals(sideBefore, after.rect(pnnh.mcfloatingbackpack.inventory.PanelArea.LEFT).w());
        assertEquals(sideBefore, after.rect(pnnh.mcfloatingbackpack.inventory.PanelArea.RIGHT).w());
        assertEquals(bottomBefore, after.rect(pnnh.mcfloatingbackpack.inventory.PanelArea.BOTTOM).h());
        assertEquals(PanelSlots.COLS - 4, state.getVisibleCols());
        assertEquals(state.getVisibleCols() + 2, after.visibleCount(pnnh.mcfloatingbackpack.inventory.PanelArea.BOTTOM));
    }

    @Test
    void theSideColumnsAndTheBottomRowComeOffTheScreenBudget() {
        // A GUI exactly 11 cells + padding wide fits 9 main columns; one pixel less fits only 8.
        int exact = 2 * PanelSlots.PAD_X + 11 * PanelSlots.SLOT_SIZE;
        state.setTotalRows(4);
        state.layout(exact, 1000);
        assertEquals(9, state.getVisibleCols());
        state.clearSizePreference();
        state.layout(exact - 1, 1000);
        assertEquals(8, state.getVisibleCols());
    }
}
