package pnnh.mcfloatingbackpack.inventory.quick;

/**
 * 067: where the quick-access grid sits. A window that can take the preferred 9×4 does; a smaller window shrinks to what fits
 * (at least one cell); fewer items shrink it further, so there is no empty row or column. Extra rows scroll vertically.
 * Pure logic: pixels in, a fit and a hit index out.
 */
public final class QuickViewport {
    /** Drawn item edge, twice a vanilla 16px icon, so the grid reads at world scale. */
    public static final int ITEM = 32;
    /** Cell edge. The difference from {@link #ITEM} is the gap between icons. */
    public static final int SLOT = 40;
    public static final int MARGIN = 18;
    public static final int PREFERRED_COLUMNS = 9;
    public static final int PREFERRED_ROWS = 4;

    private QuickViewport() {
    }

    /**
     * @param columns cells across the drawn grid (the wrap width, or less when everything is one short row)
     * @param rows cells down the drawn grid
     * @param scroll first drawn content row; clamped
     * @param totalRows content rows, including those scrolled out of sight
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
            boolean moreBelow) {
    }

    public static Fit layout(int screenWidth, int screenHeight, int count, int scroll) {
        if (count <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return new Fit(0, 0, 0, 0, 0, 0, Math.max(count, 0), false, false);
        }
        int capColumns = clamp(1, PREFERRED_COLUMNS, (screenWidth - 2 * MARGIN) / SLOT);
        int capRows = clamp(1, PREFERRED_ROWS, (screenHeight - 2 * MARGIN) / SLOT);
        int totalRows = (count + capColumns - 1) / capColumns;
        int rows = Math.min(capRows, totalRows);
        int columns = totalRows <= 1 ? Math.min(capColumns, count) : capColumns;
        int maxScroll = Math.max(0, totalRows - rows);
        int clamped = Math.max(0, Math.min(scroll, maxScroll));
        int originX = Math.max(0, (screenWidth - columns * SLOT) / 2);
        int originY = Math.max(0, (screenHeight - rows * SLOT) / 2);
        return new Fit(
                originX,
                originY,
                columns,
                rows,
                clamped,
                totalRows,
                count,
                clamped > 0,
                clamped + rows < totalRows);
    }

    /**
     * @return the candidate index under the pointer, or {@code -1} when the pointer is outside the grid or on the empty part of
     * a short last row (that part is not a cell; the grid stays open)
     */
    public static int hit(Fit fit, double mouseX, double mouseY) {
        if (fit == null || fit.columns() <= 0 || fit.rows() <= 0 || fit.count() <= 0) {
            return -1;
        }
        double localX = mouseX - fit.originX();
        double localY = mouseY - fit.originY();
        if (localX < 0 || localY < 0 || localX >= fit.columns() * SLOT || localY >= fit.rows() * SLOT) {
            return -1;
        }
        int column = (int) (localX / SLOT);
        int row = (int) (localY / SLOT);
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
