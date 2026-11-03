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
        state.endScrollDrag();
        state.endResize();
        state.clearSizePreference();
    }

    @Test
    void layoutCapsVisibleRowsToScreenAndShowsScrollbar() {
        state.setTotalRows(12);
        // 40 bottom margin on a 240-tall GUI → room for fewer than 12 rows.
        state.layout(WIDE_SCREEN, 240);

        assertTrue(state.getVisibleRows() < 12);
        assertTrue(state.needsScrollbar());
        assertEquals(PanelSlots.panelWidth(true), state.getWidth());
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
    void layoutFitsAllRowsWithoutScrollbarWhenTallEnough() {
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(4, state.getVisibleRows());
        assertFalse(state.needsScrollbar());
        assertEquals(PanelSlots.panelWidth(false), state.getWidth());
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
    void beginResizeClearsDraggingAndScrolling() {
        state.beginDrag(0, 0);
        assertTrue(state.isDragging());

        state.beginResize(0, 0);

        assertTrue(state.isResizing());
        assertFalse(state.isDragging());
        assertFalse(state.isScrolling());
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
}
