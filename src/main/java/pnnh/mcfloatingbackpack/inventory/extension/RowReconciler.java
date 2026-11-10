package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 035: brings the rows of a backpack in line with the extension packs it holds. For every category the number of visible rows
 * becomes the number of packs placed (in the three tool areas, plus the one on the panel cursor):
 *
 * <ul>
 *   <li>a pack taken away: an empty row of that category is deleted, the last one first; only when there is none is the last
 *       visible row of the category hidden, items and all (it goes to the end of the hidden list);
 *   <li>a pack placed: the hidden row of that category that was hidden first comes back, at the end of the visible rows; if
 *       there is none a new empty row is added, unless the backpack already has the most rows, in which case nothing is
 *       added and the category is reported as refused.
 * </ul>
 *
 * <p>Nothing that holds an item is ever deleted: a row with something in it is hidden, not dropped. The result does not
 * depend on the order in which packs were placed and taken. Pure logic with no Minecraft types.
 */
public final class RowReconciler {
    /**
     * @param rows the rows after the reconcile
     * @param layoutChanged whether the categories of the visible rows (and so the numbering of the main area) differ
     * @param refused categories for which a pack was placed but no row could be added
     */
    public record Result<T>(RowSet<T> rows, boolean layoutChanged, List<String> refused) {
    }

    private RowReconciler() {
    }

    public static <T> Result<T> reconcile(
            RowSet<T> rows, Map<String, Integer> placed, Predicate<T> isEmpty, Supplier<T> emptyCell, int maxRows) {
        List<Row<T>> visible = new ArrayList<>(rows.visible());
        List<Row<T>> hidden = new ArrayList<>(rows.hidden());
        List<String> refused = new ArrayList<>();

        for (String category : categoriesOf(rows, placed)) {
            int wanted = Math.max(0, placed.getOrDefault(category, 0));
            int have = count(visible, category);
            while (have > wanted) {
                int empty = lastEmpty(visible, category, isEmpty);
                if (empty >= 0) {
                    visible.remove(empty);
                } else {
                    hidden.add(visible.remove(lastOf(visible, category)));
                }
                have--;
            }
            while (have < wanted) {
                int back = firstOf(hidden, category);
                if (back >= 0) {
                    visible.add(hidden.remove(back));
                } else if (visible.size() + hidden.size() < maxRows) {
                    List<T> cells = new ArrayList<>(ExtensionCategories.ROW_CELLS);
                    for (int i = 0; i < ExtensionCategories.ROW_CELLS; i++) {
                        cells.add(emptyCell.get());
                    }
                    visible.add(new Row<>(category, cells));
                } else {
                    refused.add(category);
                    break;
                }
                have++;
            }
        }

        RowSet<T> result = new RowSet<>(visible, hidden);
        return new Result<>(result, !result.visibleCategories().equals(rows.visibleCategories()), refused);
    }

    /** The known categories in their fixed order, then any other id that appears, in the order it appears. */
    private static <T> List<String> categoriesOf(RowSet<T> rows, Map<String, Integer> placed) {
        List<String> out = new ArrayList<>(ExtensionCategories.ALL);
        for (Row<T> row : rows.visible()) {
            if (!out.contains(row.category())) {
                out.add(row.category());
            }
        }
        for (Row<T> row : rows.hidden()) {
            if (!out.contains(row.category())) {
                out.add(row.category());
            }
        }
        for (String category : placed.keySet()) {
            if (!out.contains(category)) {
                out.add(category);
            }
        }
        return out;
    }

    private static <T> int count(List<Row<T>> rows, String category) {
        int n = 0;
        for (Row<T> row : rows) {
            if (row.category().equals(category)) {
                n++;
            }
        }
        return n;
    }

    private static <T> int firstOf(List<Row<T>> rows, String category) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).category().equals(category)) {
                return i;
            }
        }
        return -1;
    }

    private static <T> int lastOf(List<Row<T>> rows, String category) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).category().equals(category)) {
                return i;
            }
        }
        return -1;
    }

    private static <T> int lastEmpty(List<Row<T>> rows, String category, Predicate<T> isEmpty) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row<T> row = rows.get(i);
            if (row.category().equals(category) && row.cells().stream().allMatch(isEmpty)) {
                return i;
            }
        }
        return -1;
    }
}
