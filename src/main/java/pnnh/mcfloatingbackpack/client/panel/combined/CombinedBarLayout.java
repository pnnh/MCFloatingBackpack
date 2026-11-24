package pnnh.mcfloatingbackpack.client.panel.combined;

/**
 * 065: the geometry of the combined bars: a title bar and, under it, a strip of tabs, hung on the top edge of the floating panel (above
 * it, outside its rectangle, like the trash can page hangs off its right edge). Pure arithmetic with no Minecraft types, so the hit tests
 * and the scrolling are unit-tested. Tab widths come from the caller (they depend on the font); everything else is fixed.
 */
public final class CombinedBarLayout {
    public static final int TITLE_H = 14;
    public static final int TAB_H = 16;
    /** The two bars together: how far above the panel's top edge they reach. */
    public static final int BARS_H = TITLE_H + TAB_H;
    public static final int ARROW_W = 10;
    public static final int TAB_GAP = 2;
    private static final int EDGE = 2;

    /** A rectangle in GUI pixels. */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final int posX;
    private final int posY;
    private final int width;
    private final int[] tabWidths;
    private final int contentWidth;
    private final boolean overflow;
    private final int areaLeft;
    private final int areaWidth;

    private CombinedBarLayout(int posX, int posY, int width, int[] tabWidths) {
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.tabWidths = tabWidths.clone();
        int total = 0;
        for (int i = 0; i < tabWidths.length; i++) {
            total += tabWidths[i] + (i > 0 ? TAB_GAP : 0);
        }
        this.contentWidth = total;
        int plain = Math.max(0, width - 2 * EDGE);
        this.overflow = total > plain;
        this.areaLeft = posX + EDGE + (overflow ? ARROW_W + EDGE : 0);
        this.areaWidth = Math.max(0, plain - (overflow ? 2 * (ARROW_W + EDGE) : 0));
    }

    /**
     * @param posX the floating panel's left edge
     * @param posY the floating panel's top edge (the bars end here)
     * @param width the floating panel's width
     * @param tabWidths the width of each tab, in tab order
     */
    public static CombinedBarLayout of(int posX, int posY, int width, int[] tabWidths) {
        return new CombinedBarLayout(posX, posY, width, tabWidths);
    }

    public Rect titleRect() {
        return new Rect(posX, posY - BARS_H, width, TITLE_H);
    }

    public Rect tabBarRect() {
        return new Rect(posX, posY - TAB_H, width, TAB_H);
    }

    /** Both bars. */
    public Rect barsRect() {
        return new Rect(posX, posY - BARS_H, width, BARS_H);
    }

    public boolean contains(double x, double y) {
        return barsRect().contains(x, y);
    }

    /** Whether the tabs need the scroll arrows. */
    public boolean overflows() {
        return overflow;
    }

    public Rect leftArrow() {
        return new Rect(posX + EDGE, posY - TAB_H, ARROW_W, TAB_H);
    }

    public Rect rightArrow() {
        return new Rect(posX + width - EDGE - ARROW_W, posY - TAB_H, ARROW_W, TAB_H);
    }

    /** The strip the tabs are drawn and hit in (tabs are clipped to it). */
    public Rect tabArea() {
        return new Rect(areaLeft, posY - TAB_H, areaWidth, TAB_H);
    }

    /** Most the tabs can be scrolled: how much wider than the strip they are. */
    public int maxScroll() {
        return overflow ? Math.max(0, contentWidth - areaWidth) : 0;
    }

    public int clampScroll(int scroll) {
        return Math.min(Math.max(0, scroll), maxScroll());
    }

    /** The scroll that brings the whole of tab {@code index} into the strip (the least change from {@code scroll}). */
    public int scrollToShow(int index, int scroll) {
        if (index < 0 || index >= tabWidths.length || !overflow) {
            return clampScroll(scroll);
        }
        int left = tabOffset(index);
        int right = left + tabWidths[index];
        int result = scroll;
        if (left < scroll) {
            result = left;
        } else if (right > scroll + areaWidth) {
            result = right - areaWidth;
        }
        return clampScroll(result);
    }

    private int tabOffset(int index) {
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset += tabWidths[i] + TAB_GAP;
        }
        return offset;
    }

    /** Tab {@code index} where it would be with the tabs scrolled by {@code scroll}, not clipped. */
    public Rect tabRect(int index, int scroll) {
        return new Rect(areaLeft + tabOffset(index) - scroll, posY - TAB_H, tabWidths[index], TAB_H);
    }

    /** The part of the tab inside the strip, or {@code null} if none of it is. */
    public Rect visibleTabRect(int index, int scroll) {
        Rect tab = tabRect(index, scroll);
        int left = Math.max(tab.x(), areaLeft);
        int right = Math.min(tab.x() + tab.w(), areaLeft + areaWidth);
        return right > left ? new Rect(left, tab.y(), right - left, tab.h()) : null;
    }

    /** The tab under the point (only the part inside the strip counts), or -1. */
    public int hitTab(double x, double y, int scroll) {
        for (int i = 0; i < tabWidths.length; i++) {
            Rect visible = visibleTabRect(i, scroll);
            if (visible != null && visible.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /** -1 for the left arrow, 1 for the right arrow, 0 for neither (and for both when the tabs do not overflow). */
    public int hitArrow(double x, double y) {
        if (!overflow) {
            return 0;
        }
        if (leftArrow().contains(x, y)) {
            return -1;
        }
        return rightArrow().contains(x, y) ? 1 : 0;
    }

    public int tabCount() {
        return tabWidths.length;
    }
}
