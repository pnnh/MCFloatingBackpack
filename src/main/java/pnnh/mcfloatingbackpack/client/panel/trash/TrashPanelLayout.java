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
    /** The two icons at the right end of the title bar (022). */
    public static final int ICON = 9;
    public static final int ICON_GAP = 2;
    public static final int ICON_PAD = 4;

    public enum Icon {
        /** Moves the trash-tagged stacks of the main area into the trash can. */
        DUMP,
        /** Moves the selected main-area stacks into the trash can. */
        DELETE
    }

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

    /** An icon's rectangle in the title bar: delete at the right end, dump to its left. */
    public Rect iconRect(Icon icon) {
        int deleteX = x + width() - ICON_PAD - ICON;
        int iconX = icon == Icon.DELETE ? deleteX : deleteX - ICON_GAP - ICON;
        return new Rect(iconX, y + (TITLE_H - ICON) / 2, ICON, ICON);
    }

    /** The icon under the point, or null. */
    public Icon iconAt(double px, double py) {
        for (Icon icon : Icon.values()) {
            if (iconRect(icon).contains(px, py)) {
                return icon;
            }
        }
        return null;
    }

    /** How wide the title text may be: it starts a little inside the bar and stops short of the dump icon. */
    public int titleTextWidth() {
        return iconRect(Icon.DUMP).x() - 2 - (x + PAD + 1);
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
