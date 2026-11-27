package pnnh.mcfloatingbackpack.inventory.quick;

/**
 * 067, 068: where the quick-access grid sits. A window tall enough for six large rows uses those; a shorter window uses the
 * smaller cells so six rows still fit. The width is always a whole row of the columns that fit, so a short last row
 * still has empty cells after the items. Extra empty rows are not added. Extra content rows scroll vertically. Pure
 * logic: pixels in, a fit and a hit index out. The empty cells are only a place to paint; a hit on one is a miss.
 */
public final class QuickViewport {
    /** Large drawn item edge, twice a vanilla 16px icon. */
    public static final int ITEM = 32;
    /** Large cell edge. The difference from {@link #ITEM} is the gap between icons. */
    public static final int SLOT = 40;
    /** Smaller item, used when six large rows do not fit. */
    public static final int SMALL_ITEM = 24;
    /** Smaller cell. The gap stays the same eight pixels. */
    public static final int SMALL_SLOT = 32;
    public static final int MARGIN = 18;
    public static final int PREFERRED_COLUMNS = 9;
    public static final int PREFERRED_ROWS = 6;

    private QuickViewport() {
    }

    /**
     * @param columns cells across the drawn grid, always the full row that fits
     * @param rows cells down the drawn grid
     * @param scroll first drawn content row; clamped
     * @param totalRows content rows, including those scrolled out of sight
     * @param item drawn item edge for this fit
     * @param slot cell edge for this fit; drawing and hit testing both use it
     */
    public record Fit(
            int originX,
            int originY,
            int columns,
            int rows,
            int scroll,
            int totalRows,
            int count,
            boolean moreAbove,
            boolean moreBelow,
            int item,
            int slot) {
    }

    public static Fit layout(int screenWidth, int screenHeight, int count, int scroll) {
        if (count <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return new Fit(0, 0, 0, 0, 0, 0, Math.max(count, 0), false, false, ITEM, SLOT);
        }
        boolean large = (screenHeight - 2 * MARGIN) / SLOT >= PREFERRED_ROWS;
        int slot = large ? SLOT : SMALL_SLOT;
        int item = large ? ITEM : SMALL_ITEM;
        int capColumns = clamp(1, PREFERRED_COLUMNS, (screenWidth - 2 * MARGIN) / slot);
        int capRows = clamp(1, PREFERRED_ROWS, (screenHeight - 2 * MARGIN) / slot);
        int columns = capColumns;
        int totalRows = (count + columns - 1) / columns;
        int rows = Math.min(capRows, totalRows);
        int maxScroll = Math.max(0, totalRows - rows);
        int clamped = Math.max(0, Math.min(scroll, maxScroll));
        int originX = Math.max(0, (screenWidth - columns * slot) / 2);
        int originY = Math.max(0, (screenHeight - rows * slot) / 2);
        return new Fit(
                originX,
                originY,
                columns,
                rows,
                clamped,
                totalRows,
                count,
                clamped > 0,
                clamped + rows < totalRows,
                item,
                slot);
    }

    /**
     * @return the candidate index under the pointer, or {@code -1} when the pointer is outside the grid or on an empty cell
     * of a short row (that cell is painted, but it is not a click)
     */
    public static int hit(Fit fit, double mouseX, double mouseY) {
        if (fit == null || fit.columns() <= 0 || fit.rows() <= 0 || fit.count() <= 0) {
            return -1;
        }
        int slot = fit.slot();
        double localX = mouseX - fit.originX();
        double localY = mouseY - fit.originY();
        if (localX < 0 || localY < 0 || localX >= fit.columns() * slot || localY >= fit.rows() * slot) {
            return -1;
        }
        int column = (int) (localX / slot);
        int row = (int) (localY / slot);
        int index = (fit.scroll() + row) * fit.columns() + column;
        return index >= 0 && index < fit.count() ? index : -1;
    }

    private static int clamp(int min, int max, int value) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
