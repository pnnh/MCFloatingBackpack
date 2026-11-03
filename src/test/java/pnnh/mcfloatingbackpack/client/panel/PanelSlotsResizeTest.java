package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the 007 reflow behavior: PanelSlots geometry keyed off the player's current column
 * setting instead of the fixed 9-column constant, plus the new resize-handle/close-button hit
 * tests on FloatingPanelState. Pure logic, no Minecraft bootstrap required.
 */
class PanelSlotsResizeTest {
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
    }

    private void resizeTo(int cols, int rows) {
        state.setTotalRows(Math.max(rows, 1));
        state.layout(WIDE_SCREEN, 400);
        state.beginResize(0, 0);
        int deltaCols = cols - state.getVisibleCols();
        int deltaRows = rows - state.getVisibleRows();
        state.updateResize(deltaCols * PanelSlots.SLOT_SIZE, deltaRows * PanelSlots.SLOT_SIZE);
        state.endResize();
        state.layout(WIDE_SCREEN, 400);
    }

    @Test
    void slotXAndSlotYReflowAroundTheCurrentColumnCount() {
        resizeTo(5, 8);

        // index 0..4 is row 0, index 5..9 is row 1, under 5 columns (not the old fixed 9).
        assertEquals(PanelSlots.slotX(state, 0), PanelSlots.slotX(state, 5));
        assertEquals(state.getPosY() + PanelSlots.PAD_TOP, PanelSlots.slotY(state, 0));
        assertEquals(
                state.getPosY() + PanelSlots.PAD_TOP + PanelSlots.SLOT_SIZE, PanelSlots.slotY(state, 5));
        assertTrue(PanelSlots.isRowVisible(state, 4));
        assertEquals(
                state.getPosX() + PanelSlots.PAD_X + 4 * PanelSlots.SLOT_SIZE, PanelSlots.slotX(state, 4));
    }

    @Test
    void indexAtFindsAReflowedSlotByItsOnScreenPosition() {
        resizeTo(5, 8);

        int x = PanelSlots.slotX(state, 6);
        int y = PanelSlots.slotY(state, 6);
        assertEquals(6, PanelSlots.indexAt(state, x + 1, y + 1));
    }

    @Test
    void hitsResizeHandleOnlyInsideTheBottomRightCorner() {
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);
        int x2 = state.getPosX() + state.getWidth();
        int y2 = state.getPosY() + state.getHeight();

        assertTrue(state.hitsResizeHandle(x2 - 1, y2 - 1));
        assertFalse(state.hitsResizeHandle(x2 - FloatingPanelState.RESIZE_HANDLE_SIZE - 5, y2 - 1));
        assertFalse(state.hitsResizeHandle(x2 - 1, y2 - FloatingPanelState.RESIZE_HANDLE_SIZE - 5));
    }

    @Test
    void hitsCloseButtonOnlyNearTheTopRightOfTheDragBar() {
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);
        int x2 = state.getPosX() + state.getWidth();

        assertTrue(state.hitsCloseButton(x2 - 5, state.getPosY() + 4));
        assertFalse(state.hitsCloseButton(state.getPosX() + 5, state.getPosY() + 4));
        assertFalse(state.hitsCloseButton(x2 - 5, state.getPosY() + FloatingPanelState.DRAG_BAR_HEIGHT + 5));
    }
}
