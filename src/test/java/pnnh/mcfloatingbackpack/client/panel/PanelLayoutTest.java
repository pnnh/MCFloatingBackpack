package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanelLayoutTest {
    private FloatingPanelState state;

    @BeforeEach
    void setUp() {
        state = FloatingPanelState.get();
        state.setVisible(true);
        state.setPos(FloatingPanelState.DEFAULT_POS_X, FloatingPanelState.DEFAULT_POS_Y);
        state.setScrollRow(0);
        state.endDrag();
        state.endScrollDrag();
    }

    @Test
    void layoutCapsVisibleRowsToScreenAndShowsScrollbar() {
        state.setTotalRows(12);
        // 20 top + 40 bottom margin → room for fewer than 12 rows on a short GUI.
        state.layout(240);

        assertTrue(state.getVisibleRows() < 12);
        assertTrue(state.needsScrollbar());
        assertEquals(PanelSlots.panelWidth(true), state.getWidth());
        assertEquals(PanelSlots.panelHeight(state.getVisibleRows()), state.getHeight());
        assertTrue(
                state.getPosY() + state.getHeight()
                        <= 240 - FloatingPanelState.BOTTOM_HUD_MARGIN + 1);
    }

    @Test
    void layoutFitsAllRowsWithoutScrollbarWhenTallEnough() {
        state.setTotalRows(4);
        state.layout(400);

        assertEquals(4, state.getVisibleRows());
        assertFalse(state.needsScrollbar());
        assertEquals(PanelSlots.panelWidth(false), state.getWidth());
    }

    @Test
    void scrollByClampsToMax() {
        state.setTotalRows(12);
        state.layout(240);
        int max = state.getTotalRows() - state.getVisibleRows();

        state.scrollBy(100);
        assertEquals(max, state.getScrollRow());
        state.scrollBy(-100);
        assertEquals(0, state.getScrollRow());
    }
}
