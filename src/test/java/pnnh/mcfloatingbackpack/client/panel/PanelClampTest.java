package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanelClampTest {
    private FloatingPanelState state;

    @BeforeEach
    void setUp() {
        state = FloatingPanelState.get();
        state.setVisible(false);
        state.setPos(FloatingPanelState.DEFAULT_POS_X, FloatingPanelState.DEFAULT_POS_Y);
        state.endDrag();
    }

    @Test
    void insideScreenUnchanged() {
        state.setPos(40, 50);
        FloatingPanelState.clampToScreen(state, 400, 300);
        assertEquals(40, state.getPosX());
        assertEquals(50, state.getPosY());
    }

    @Test
    void leftAndTopOverflowPushedIn() {
        state.setPos(-80, -40);
        FloatingPanelState.clampToScreen(state, 400, 300);
        assertEquals(0, state.getPosX());
        assertEquals(0, state.getPosY());
    }

    @Test
    void rightAndBottomOverflowKeepsDragBarVisible() {
        state.setPos(10_000, 10_000);
        int screenW = 400;
        int screenH = 300;
        FloatingPanelState.clampToScreen(state, screenW, screenH);
        assertEquals(screenW - state.getWidth(), state.getPosX());
        assertEquals(screenH - FloatingPanelState.DRAG_BAR_HEIGHT, state.getPosY());
        assertTrue(state.getPosY() + FloatingPanelState.DRAG_BAR_HEIGHT <= screenH);
    }

    @Test
    void narrowScreenStillKeepsOrigin() {
        state.setPos(-5, 20);
        FloatingPanelState.clampToScreen(state, 50, 40);
        assertEquals(0, state.getPosX());
        assertTrue(state.getPosY() >= 0);
        assertTrue(state.getPosY() <= 40 - FloatingPanelState.DRAG_BAR_HEIGHT);
    }
}
