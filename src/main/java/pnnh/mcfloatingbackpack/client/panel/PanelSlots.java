package pnnh.mcfloatingbackpack.client.panel;

/**
 * Shared 9-column slot layout. Row count comes from the open backpack.
 */
public final class PanelSlots {
    public static final int SLOT_SIZE = 18;
    public static final int COLS = 9;
    public static final int PAD_X = 7;
    public static final int PAD_TOP = 17;
    public static final int PAD_BOTTOM = 7;
    /** Gap between the last slot column and the scrollbar track. */
    public static final int SCROLLBAR_GAP = 2;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int SCROLL_THUMB_MIN = 10;

    private PanelSlots() {
    }

    /**
     * @param cols current display column count (007: player-adjustable, 3..{@link #COLS})
     */
    public static int panelWidth(int cols, boolean scrollbar) {
        int width = PAD_X * 2 + cols * SLOT_SIZE;
        if (scrollbar) {
            width += SCROLLBAR_GAP + SCROLLBAR_WIDTH;
        }
        return width;
    }

    /** Fixed-9-column width, used only for the pre-layout default size. */
    public static int panelWidth(boolean scrollbar) {
        return panelWidth(COLS, scrollbar);
    }

    public static int panelWidth() {
        return panelWidth(false);
    }

    public static int panelHeight(int visibleRows) {
        int rows = Math.max(1, visibleRows);
        return PAD_TOP + rows * SLOT_SIZE + PAD_BOTTOM;
    }

    public static int slotX(FloatingPanelState state, int index) {
        int col = index % state.getVisibleCols();
        return state.getPosX() + PAD_X + col * SLOT_SIZE;
    }

    public static int slotY(FloatingPanelState state, int index) {
        int row = index / state.getVisibleCols() - state.getScrollRow();
        return state.getPosY() + PAD_TOP + row * SLOT_SIZE;
    }

    public static boolean isRowVisible(FloatingPanelState state, int index) {
        int row = index / state.getVisibleCols();
        return row >= state.getScrollRow() && row < state.getScrollRow() + state.getVisibleRows();
    }

    /** @return global slot index, or -1 if the point is not inside a visible slot */
    public static int indexAt(FloatingPanelState state, double mouseX, double mouseY) {
        int total = state.getTotalRows() * state.getVisibleCols();
        for (int i = 0; i < total; i++) {
            if (!isRowVisible(state, i)) {
                continue;
            }
            int x = slotX(state, i);
            int y = slotY(state, i);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    public static int scrollbarTrackX(FloatingPanelState state) {
        return state.getPosX() + state.getWidth() - PAD_X - SCROLLBAR_WIDTH;
    }

    public static int scrollbarTrackY(FloatingPanelState state) {
        return state.getPosY() + PAD_TOP;
    }

    public static int scrollbarTrackHeight(FloatingPanelState state) {
        return Math.max(1, state.getVisibleRows()) * SLOT_SIZE;
    }

    public static int scrollbarThumbHeight(FloatingPanelState state) {
        int track = scrollbarTrackHeight(state);
        int total = Math.max(1, state.getTotalRows());
        int visible = Math.max(1, state.getVisibleRows());
        if (total <= visible) {
            return track;
        }
        return Math.max(SCROLL_THUMB_MIN, track * visible / total);
    }

    public static int scrollbarThumbY(FloatingPanelState state) {
        int trackY = scrollbarTrackY(state);
        int track = scrollbarTrackHeight(state);
        int thumb = scrollbarThumbHeight(state);
        int maxScroll = Math.max(0, state.getTotalRows() - state.getVisibleRows());
        if (maxScroll <= 0 || track <= thumb) {
            return trackY;
        }
        int travel = track - thumb;
        return trackY + travel * state.getScrollRow() / maxScroll;
    }

    public static boolean hitsScrollbar(FloatingPanelState state, double mouseX, double mouseY) {
        if (!state.needsScrollbar()) {
            return false;
        }
        int x = scrollbarTrackX(state);
        int y = scrollbarTrackY(state);
        int h = scrollbarTrackHeight(state);
        return mouseX >= x
                && mouseX < x + SCROLLBAR_WIDTH
                && mouseY >= y
                && mouseY < y + h;
    }

    public static boolean hitsScrollThumb(FloatingPanelState state, double mouseX, double mouseY) {
        if (!hitsScrollbar(state, mouseX, mouseY)) {
            return false;
        }
        int thumbY = scrollbarThumbY(state);
        int thumbH = scrollbarThumbHeight(state);
        return mouseY >= thumbY && mouseY < thumbY + thumbH;
    }

    /**
     * Maps a mouse Y on the track to a scroll row.
     */
    public static int scrollRowAt(FloatingPanelState state, double mouseY) {
        int maxScroll = Math.max(0, state.getTotalRows() - state.getVisibleRows());
        if (maxScroll <= 0) {
            return 0;
        }
        int trackY = scrollbarTrackY(state);
        int track = scrollbarTrackHeight(state);
        int thumb = scrollbarThumbHeight(state);
        int travel = Math.max(1, track - thumb);
        double relative = mouseY - trackY - thumb / 2.0;
        int row = (int) Math.round(relative * maxScroll / travel);
        return Math.max(0, Math.min(maxScroll, row));
    }
}
