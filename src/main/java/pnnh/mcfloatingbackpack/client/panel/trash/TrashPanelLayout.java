package pnnh.mcfloatingbackpack.client.panel.trash;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;

/**
 * Geometry of the trash can panel (021): it hangs off the floating panel's right edge, its top aligned with the
 * panel's top, and is a title bar over a 3x3 grid of cells. Pure arithmetic with no Minecraft types, so it can be
 * unit-tested; the renderer and the input both read this one model.
 *
 * <p>The frame's left outline pixel is the floating panel's own right outline pixel, so the two look joined. The
 * panel lies entirely outside the floating panel's rectangle, which is why presses on it must be claimed separately.
 */
public final class TrashPanelLayout {
    public static final int TITLE_H = 14;
    public static final int PAD = 4;
    public static final int COLS = 3;
    public static final int ROWS = 3;
    public static final int CELLS = COLS * ROWS;

    private final int x;
    private final int y;

    private TrashPanelLayout(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** @param panelWidth the floating panel's whole width in GUI pixels */
    public static TrashPanelLayout of(int panelX, int panelY, int panelWidth) {
        return new TrashPanelLayout(panelX + panelWidth + 1, panelY);
    }

    public static int width() {
        return 2 * PAD + COLS * PanelLayout.SLOT;
    }

    public static int height() {
        return TITLE_H + ROWS * PanelLayout.SLOT + PAD;
    }

    /** The whole frame: title bar, padding and cells. */
    public Rect rect() {
        return new Rect(x, y, width(), height());
    }

    public Rect titleRect() {
        return new Rect(x, y, width(), TITLE_H);
    }

    /** Cell {@code i} (0..8), counted left to right, top to bottom. */
    public Rect cellRect(int i) {
        int col = i % COLS;
        int row = i / COLS;
        return new Rect(x + PAD + col * PanelLayout.SLOT, y + TITLE_H + row * PanelLayout.SLOT, PanelLayout.SLOT, PanelLayout.SLOT);
    }

    /** The cell under the point (0..8), or -1 on the title bar, the padding or outside. */
    public int cellAt(double px, double py) {
        for (int i = 0; i < CELLS; i++) {
            if (cellRect(i).contains(px, py)) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(double px, double py) {
        return rect().contains(px, py);
    }
}
