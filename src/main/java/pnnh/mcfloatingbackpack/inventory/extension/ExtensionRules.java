package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 035: the placement rules of the category extensions. The main area is the cells of the tier capacity followed by the cells
 * of the visible rows, nine per row in the order of the rows; the hidden rows are not in it at all. Putting something into a
 * row cell asks {@link CategoryMatcher}; taking something out is always allowed, so nothing here is about taking.
 * Pure logic with no Minecraft types.
 */
public final class ExtensionRules {
    private ExtensionRules() {
    }

    /** The cells of the tier capacity take anything; a visible row's cell takes what fits its category; the rest takes nothing. */
    public static <T> boolean canPlace(
            RowSet<T> rows, int baseCapacity, int slot, T stack, CategoryMatcher<T> matcher) {
        return canPlace(rows.visibleCategories(), baseCapacity, slot, stack, matcher);
    }

    /**
     * 037: the same rule from the categories of the visible rows alone, which is all the client is told (it has no cells of the
     * hidden rows). The version above delegates here, so the server and the panel's prediction run the same code.
     */
    public static <T> boolean canPlace(
            List<String> visibleCategories, int baseCapacity, int slot, T stack, CategoryMatcher<T> matcher) {
        if (slot < 0) {
            return false;
        }
        if (slot < baseCapacity) {
            return true;
        }
        int row = (slot - baseCapacity) / ExtensionCategories.ROW_CELLS;
        return row < visibleCategories.size() && matcher.matches(visibleCategories.get(row), stack);
    }

    /** Which visible row a main-area slot is in: -1 for the tier capacity's cells, -2 for a slot that is in no visible row. */
    public static int rowOfSlot(int baseCapacity, int visibleRowCount, int slot) {
        if (slot < 0) {
            return -2;
        }
        if (slot < baseCapacity) {
            return -1;
        }
        int row = (slot - baseCapacity) / ExtensionCategories.ROW_CELLS;
        return row < visibleRowCount ? row : -2;
    }

    /**
     * Where a stack goes when it is moved into the backpack, in three segments to be tried one after the other (each with the
     * "top up the matching stacks, then fill the empty cells" rule of {@code QuickTransferLogic.moveInto}, what is left of one
     * going on to the next): first the cells of the visible rows of a category the stack fits, row by row; then the tier
     * capacity's cells; and last the cells of the universal rows (038), which are the overflow: they take whatever the main
     * area had no room for, so that a universal row does not swallow everything before the main grid is used. With no matching
     * row the first segment is empty, with no universal row the last is, which is how things were before the rows.
     */
    public static <T> List<int[]> targetSegments(
            RowSet<T> rows, int baseCapacity, T stack, CategoryMatcher<T> matcher) {
        return targetSegments(rows.visibleCategories(), baseCapacity, stack, matcher);
    }

    /** 037: {@link #targetSegments(RowSet, int, Object, CategoryMatcher)} from the categories of the visible rows alone. */
    public static <T> List<int[]> targetSegments(
            List<String> visibleCategories, int baseCapacity, T stack, CategoryMatcher<T> matcher) {
        List<Integer> matching = new ArrayList<>();
        List<Integer> universal = new ArrayList<>();
        for (int row = 0; row < visibleCategories.size(); row++) {
            String category = visibleCategories.get(row);
            if (matcher.matches(category, stack)) {
                int first = baseCapacity + row * ExtensionCategories.ROW_CELLS;
                List<Integer> into = ExtensionCategories.GENERAL.equals(category) ? universal : matching;
                for (int cell = 0; cell < ExtensionCategories.ROW_CELLS; cell++) {
                    into.add(first + cell);
                }
            }
        }
        int[] rowCells = matching.stream().mapToInt(Integer::intValue).toArray();
        int[] main = new int[baseCapacity];
        for (int i = 0; i < baseCapacity; i++) {
            main[i] = i;
        }
        int[] universalCells = universal.stream().mapToInt(Integer::intValue).toArray();
        return List.of(rowCells, main, universalCells);
    }

    /** How many rows must be added for the packs placed to all have one: what exceeds the rows of each category already there. */
    public static <T> int newRowsNeeded(RowSet<T> rows, Map<String, Integer> placedAfter) {
        int needed = 0;
        for (Map.Entry<String, Integer> entry : placedAfter.entrySet()) {
            int existing = rows.visibleOf(entry.getKey()) + rows.hiddenOf(entry.getKey());
            needed += Math.max(0, entry.getValue() - existing);
        }
        return needed;
    }

    /** Whether the packs placed can all have a row without going past {@code maxRows} (bringing a hidden row back is free). */
    public static <T> boolean fits(RowSet<T> rows, Map<String, Integer> placedAfter, int maxRows) {
        return newRowsNeeded(rows, placedAfter) <= Math.max(0, maxRows - rows.total());
    }
}
