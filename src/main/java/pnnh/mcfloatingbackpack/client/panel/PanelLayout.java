package pnnh.mcfloatingbackpack.client.panel;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * Geometry of the floating panel (016): the main area in the middle, one column on each side, one row at the
 * bottom that runs under the side columns too, so the panel is one rectangle without empty corners. Every
 * area scrolls on its own; the visible cells of each area follow the main area's visible columns and rows.
 *
 * <p>Pure arithmetic with no Minecraft types, so it can be unit-tested. Rendering, hit-testing and the scroll
 * wheel all read this one model instead of computing positions themselves.
 */
public final class PanelLayout {
    public static final int SLOT = 18;
    public static final int PAD_X = 7;
    public static final int PAD_TOP = 17;
    public static final int PAD_BOTTOM = 7;

    /** A rectangle in GUI pixels. */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    /** A real slot under the mouse: the area and the slot's index inside it. */
    public record Hit(PanelArea area, int index) {
    }

    private final int posX;
    private final int posY;
    private final int cols;
    private final int rows;
    private final int[] counts;
    private final int[] scroll;

    private PanelLayout(int posX, int posY, int cols, int rows, int[] counts, int[] scroll) {
        this.posX = posX;
        this.posY = posY;
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        this.counts = counts.clone();
        this.scroll = scroll.clone();
    }

    /**
     * @param cols visible columns of the main area
     * @param rows visible rows of the main area
     * @param counts slot count of each area, indexed by {@link PanelArea#ordinal()}
     * @param scroll scroll offset (in cells) of each area, indexed the same way
     */
    public static PanelLayout of(int posX, int posY, int cols, int rows, int[] counts, int[] scroll) {
        return new PanelLayout(posX, posY, cols, rows, counts, scroll);
    }

    /** Whole panel width: the main columns plus one column on each side. */
    public static int width(int cols) {
        return 2 * PAD_X + (cols + 2) * SLOT;
    }

    /** Whole panel height: the main rows plus the bottom row. */
    public static int height(int rows) {
        return PAD_TOP + (rows + 1) * SLOT + PAD_BOTTOM;
    }

    public int width() {
        return width(cols);
    }

    public int height() {
        return height(rows);
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public Rect rect(PanelArea area) {
        int x0 = posX + PAD_X;
        int y0 = posY + PAD_TOP;
        return switch (area) {
            case MAIN -> new Rect(x0 + SLOT, y0, cols * SLOT, rows * SLOT);
            case LEFT -> new Rect(x0, y0, SLOT, rows * SLOT);
            case RIGHT -> new Rect(x0 + (cols + 1) * SLOT, y0, SLOT, rows * SLOT);
            case BOTTOM -> new Rect(x0, y0 + rows * SLOT, (cols + 2) * SLOT, SLOT);
        };
    }

    /** Number of cells the area currently shows (some may be fillers beyond its capacity). */
    public int visibleCount(PanelArea area) {
        return switch (area) {
            case MAIN -> cols * rows;
            case LEFT, RIGHT -> rows;
            case BOTTOM -> cols + 2;
        };
    }

    public int scroll(PanelArea area) {
        return scroll[area.ordinal()];
    }

    public int slotCount(PanelArea area) {
        return counts[area.ordinal()];
    }

    /** Virtual row count of the main area after reflowing its slots over the visible columns. */
    public int mainTotalRows() {
        return Math.max(1, (counts[PanelArea.MAIN.ordinal()] + cols - 1) / cols);
    }

    /** Largest scroll offset of the area: main and side columns scroll rows, the bottom row scrolls columns. */
    public int maxScroll(PanelArea area) {
        return switch (area) {
            case MAIN -> Math.max(0, mainTotalRows() - rows);
            case LEFT, RIGHT, BOTTOM -> Math.max(0, counts[area.ordinal()] - visibleCount(area));
        };
    }

    /** More content before the visible window (up, or left for the bottom row). */
    public boolean canBack(PanelArea area) {
        return scroll(area) > 0;
    }

    /** More content after the visible window (down, or right for the bottom row). */
    public boolean canForward(PanelArea area) {
        return scroll(area) < maxScroll(area);
    }

    /**
     * Index of the slot shown in visible cell {@code ordinal} of the area, or -1 for a filler cell (beyond the
     * area's capacity, or the empty tail of the main area's last row). For the main area the ordinal counts
     * row by row ({@code row * cols + col}).
     */
    public int slotIndex(PanelArea area, int ordinal) {
        int index = switch (area) {
            case MAIN -> (ordinal / cols + scroll(area)) * cols + ordinal % cols;
            case LEFT, RIGHT, BOTTOM -> scroll(area) + ordinal;
        };
        return index >= 0 && index < counts[area.ordinal()] ? index : -1;
    }

    public int cellX(PanelArea area, int ordinal) {
        Rect r = rect(area);
        return switch (area) {
            case MAIN -> r.x() + ordinal % cols * SLOT;
            case LEFT, RIGHT -> r.x();
            case BOTTOM -> r.x() + ordinal * SLOT;
        };
    }

    public int cellY(PanelArea area, int ordinal) {
        Rect r = rect(area);
        return switch (area) {
            case MAIN -> r.y() + ordinal / cols * SLOT;
            case LEFT, RIGHT -> r.y() + ordinal * SLOT;
            case BOTTOM -> r.y();
        };
    }

    /** The area whose rectangle contains the point (fillers included), or null. */
    public PanelArea areaAt(double x, double y) {
        for (PanelArea area : PanelArea.values()) {
            if (rect(area).contains(x, y)) {
                return area;
            }
        }
        return null;
    }

    /** The real slot under the point, or null (outside every area, or on a filler cell). */
    public Hit hit(double x, double y) {
        PanelArea area = areaAt(x, y);
        if (area == null) {
            return null;
        }
        Rect r = rect(area);
        int col = (int) ((x - r.x()) / SLOT);
        int row = (int) ((y - r.y()) / SLOT);
        int ordinal = switch (area) {
            case MAIN -> row * cols + col;
            case LEFT, RIGHT -> row;
            case BOTTOM -> col;
        };
        int index = slotIndex(area, ordinal);
        return index < 0 ? null : new Hit(area, index);
    }
}
