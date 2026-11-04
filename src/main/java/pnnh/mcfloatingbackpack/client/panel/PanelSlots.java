package pnnh.mcfloatingbackpack.client.panel;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * Shared sizes of the panel's slot layout. The geometry itself lives in {@link PanelLayout} (016); this class
 * keeps the constants other code already uses and {@link #indexAt}, which answers for the main area only so
 * that the 011/013/014 gestures never see the tool areas.
 */
public final class PanelSlots {
    public static final int SLOT_SIZE = PanelLayout.SLOT;
    public static final int COLS = 9;
    public static final int PAD_X = PanelLayout.PAD_X;
    public static final int PAD_TOP = PanelLayout.PAD_TOP;
    public static final int PAD_BOTTOM = PanelLayout.PAD_BOTTOM;

    private PanelSlots() {
    }

    /** Whole panel width for {@code cols} visible main columns (the side columns are included). */
    public static int panelWidth(int cols) {
        return PanelLayout.width(cols);
    }

    /** Default width: nine main columns. */
    public static int panelWidth() {
        return panelWidth(COLS);
    }

    /** Whole panel height for {@code visibleRows} visible main rows (the bottom row is included). */
    public static int panelHeight(int visibleRows) {
        return PanelLayout.height(Math.max(1, visibleRows));
    }

    /**
     * @return index of the main-area slot under the point (reflowed over the visible columns, scroll
     *     applied), or -1 if the point is not inside a visible main-area cell. Tool-area cells give -1. The
     *     index may exceed the real slot count in the last partial row; callers check it against the
     *     backpack's slot count.
     */
    public static int indexAt(FloatingPanelState state, double mouseX, double mouseY) {
        int virtualSlots = state.getTotalRows() * state.getVisibleCols();
        PanelLayout.Hit hit = state.layoutModel(new int[] {virtualSlots, 0, 0, 0}).hit(mouseX, mouseY);
        return hit != null && hit.area() == PanelArea.MAIN ? hit.index() : -1;
    }
}
