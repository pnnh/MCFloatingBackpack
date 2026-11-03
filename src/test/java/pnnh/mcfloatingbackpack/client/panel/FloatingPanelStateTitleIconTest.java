package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the 008 title-bar icon hit test on FloatingPanelState. Pure logic, no Minecraft
 * bootstrap required.
 */
class FloatingPanelStateTitleIconTest {
    private static final int WIDE_SCREEN = 1000;

    private FloatingPanelState state;

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
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);
    }

    @Test
    void hitsTitleIconInsideItsTopLeftBox() {
        assertTrue(state.hitsTitleIcon(state.getPosX() + 5, state.getPosY() + 2));
    }

    @Test
    void doesNotHitTitleIconPastItsRightEdge() {
        int beyondIcon = state.getPosX() + 4 + FloatingPanelState.TITLE_ICON_SIZE + 10;
        assertFalse(state.hitsTitleIcon(beyondIcon, state.getPosY() + 2));
    }

    @Test
    void doesNotHitTitleIconBelowTheDragBar() {
        assertFalse(
                state.hitsTitleIcon(
                        state.getPosX() + 5, state.getPosY() + FloatingPanelState.DRAG_BAR_HEIGHT + 5));
    }

    @Test
    void titleIconAndCloseButtonDoNotOverlap() {
        // Narrowest allowed panel (3 columns): the left-anchored icon and right-anchored close
        // button must still both fit without colliding.
        state.beginResize(0, 0);
        state.updateResize(-20 * PanelSlots.SLOT_SIZE, 0);
        state.layout(WIDE_SCREEN, 400);

        int iconRight = state.getPosX() + 4 + FloatingPanelState.TITLE_ICON_SIZE;
        int closeLeft = state.getPosX() + state.getWidth() - 3 - FloatingPanelState.CLOSE_BUTTON_SIZE;
        assertTrue(iconRight <= closeLeft);
    }
}
