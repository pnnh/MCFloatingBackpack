package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

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
    void cellsReflowAroundTheCurrentColumnCount() {
        resizeTo(5, 8);
        PanelLayout layout = state.layoutModel(new int[] {36, 4, 4, 11});
        PanelLayout.Rect main = layout.rect(PanelArea.MAIN);

        // ordinal 0..4 is row 0, 5..9 is row 1, under 5 columns (not the old fixed 9).
        assertEquals(layout.cellX(PanelArea.MAIN, 0), layout.cellX(PanelArea.MAIN, 5));
        assertEquals(main.y(), layout.cellY(PanelArea.MAIN, 0));
        assertEquals(main.y() + PanelSlots.SLOT_SIZE, layout.cellY(PanelArea.MAIN, 5));
        assertEquals(main.x() + 4 * PanelSlots.SLOT_SIZE, layout.cellX(PanelArea.MAIN, 4));
        // The main area starts one column in from the panel's left padding (the left tool column).
        assertEquals(state.getPosX() + PanelSlots.PAD_X + PanelSlots.SLOT_SIZE, main.x());
    }

    @Test
    void indexAtFindsAReflowedSlotByItsOnScreenPosition() {
        resizeTo(5, 8);
        PanelLayout layout = state.layoutModel(new int[] {state.getTotalRows() * 5, 0, 0, 0});

        int x = layout.cellX(PanelArea.MAIN, 6);
        int y = layout.cellY(PanelArea.MAIN, 6);
        assertEquals(6, PanelSlots.indexAt(state, x + 1, y + 1));
    }

    @Test
    void indexAtAnswersForTheMainAreaOnly() {
        // 011/013/014 find slots through indexAt; the tool areas must give -1 so those gestures never see them.
        resizeTo(5, 4);
        PanelLayout layout = state.layoutModel(new int[] {36, 4, 4, 11});
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.BOTTOM}) {
            PanelLayout.Rect r = layout.rect(area);
            assertEquals(-1, PanelSlots.indexAt(state, r.x() + 2, r.y() + 2));
        }
        PanelLayout.Rect main = layout.rect(PanelArea.MAIN);
        assertEquals(0, PanelSlots.indexAt(state, main.x() + 2, main.y() + 2));
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
