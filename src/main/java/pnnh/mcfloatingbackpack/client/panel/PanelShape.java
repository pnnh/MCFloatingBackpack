package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.List;

/**
 * Which panel sizes are allowed (017): the main area's (columns, visible rows) such that no area ever shows a
 * solid, unclickable filler cell, whatever it is scrolled to. Filler cells come from three places: the main
 * area's last row being only partly filled, the side columns being asked to show more rows than they have
 * slots, and the bottom row being asked to show more cells than it has slots.
 *
 * <p>Pure arithmetic with no Minecraft types, so it can be unit-tested. A backpack for which no size is valid
 * (an unusual capacity, or the counts are not known yet) is not restricted at all: the panel then behaves as
 * it did in 016 and may show filler cells.
 */
public final class PanelShape {
    public static final int MIN_CELLS = 3;
    public static final int MAX_COLS = 9;

    /** Slot counts of the four areas; 0 means "not reported yet". */
    public record Counts(int main, int left, int right, int bottom) {
    }

    private PanelShape() {
    }

    /** Most rows that can be visible at {@code cols} columns: the side columns and the main area must both fill them. */
    public static int maxRows(Counts counts, int cols) {
        return Math.min(Math.min(counts.left(), counts.right()), counts.main() / Math.max(1, cols));
    }

    /**
     * Column counts that leave no half row in the main area, fit the bottom row, and allow at least
     * {@link #MIN_CELLS} rows; ascending.
     */
    public static List<Integer> validCols(Counts counts) {
        List<Integer> cols = new ArrayList<>();
        if (counts.main() <= 0 || counts.bottom() <= 0) {
            return cols;
        }
        for (int c = MIN_CELLS; c <= MAX_COLS; c++) {
            if (counts.main() % c == 0 && c + 2 <= counts.bottom() && maxRows(counts, c) >= MIN_CELLS) {
                cols.add(c);
            }
        }
        return cols;
    }

    /** Whether sizes are restricted at all for these counts; false when no size would be valid. */
    public static boolean restricted(Counts counts) {
        return !validCols(counts).isEmpty();
    }

    public static boolean isValidCols(Counts counts, int cols) {
        return !restricted(counts) || validCols(counts).contains(cols);
    }

    public static boolean isValidRows(Counts counts, int cols, int rows) {
        if (!restricted(counts)) {
            return true;
        }
        return rows >= MIN_CELLS && rows <= maxRows(counts, cols);
    }

    public static boolean isValid(Counts counts, int cols, int rows) {
        return isValidCols(counts, cols) && isValidRows(counts, cols, rows);
    }

    /** The largest valid column count not above {@code target}; the smallest valid one if there is none. */
    public static int snapCols(Counts counts, int target) {
        if (!restricted(counts)) {
            return Math.max(MIN_CELLS, Math.min(MAX_COLS, target));
        }
        List<Integer> valid = validCols(counts);
        int best = valid.get(0);
        for (int c : valid) {
            if (c <= target) {
                best = c;
            }
        }
        return best;
    }

    /** {@code target} clamped into the valid row range for {@code cols}. */
    public static int snapRows(Counts counts, int cols, int target) {
        if (!restricted(counts)) {
            return Math.max(MIN_CELLS, target);
        }
        return Math.max(MIN_CELLS, Math.min(target, maxRows(counts, cols)));
    }
}
