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
        if (slot < 0) {
            return false;
        }
        if (slot < baseCapacity) {
            return true;
        }
        int row = (slot - baseCapacity) / ExtensionCategories.ROW_CELLS;
        return row < rows.visible().size() && matcher.matches(rows.visible().get(row).category(), stack);
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
     * Where a stack goes when it is moved into the backpack, in two segments to be tried one after the other (each with the
     * "top up the matching stacks, then fill the empty cells" rule of {@code QuickTransferLogic.moveInto}, what is left of the
     * first going on to the second): first the cells of the visible rows whose category the stack fits, row by row, then the
     * tier capacity's cells. With no matching row the first segment is empty, which is how things were before the rows.
     */
    public static <T> List<int[]> targetSegments(
            RowSet<T> rows, int baseCapacity, T stack, CategoryMatcher<T> matcher) {
        List<Integer> matching = new ArrayList<>();
        for (int row = 0; row < rows.visible().size(); row++) {
            if (matcher.matches(rows.visible().get(row).category(), stack)) {
                int first = baseCapacity + row * ExtensionCategories.ROW_CELLS;
                for (int cell = 0; cell < ExtensionCategories.ROW_CELLS; cell++) {
                    matching.add(first + cell);
                }
            }
        }
        int[] rowCells = matching.stream().mapToInt(Integer::intValue).toArray();
        int[] main = new int[baseCapacity];
        for (int i = 0; i < baseCapacity; i++) {
            main[i] = i;
        }
        return List.of(rowCells, main);
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
