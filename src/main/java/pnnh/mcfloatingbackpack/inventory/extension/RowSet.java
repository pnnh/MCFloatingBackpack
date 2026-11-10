package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 035: the rows of one backpack: the visible ones in the order they were added, and the hidden ones in the order they were
 * hidden (so "the one hidden first" is simply the first of its category). A hidden row is never empty, because an empty row
 * is deleted instead of hidden. At most {@link ExtensionCategories#MAX_ROWS} rows in all. Immutable; generic in the cell type
 * so it can be tested with plain strings (the game uses {@code ItemStack}). Pure logic with no Minecraft types.
 */
public record RowSet<T>(List<Row<T>> visible, List<Row<T>> hidden) {
    /** How many cells of one category are hidden, for the panel's hint about items it cannot show. */
    public record HiddenSummary(String category, int nonEmptyCells) {
    }

    public RowSet {
        visible = List.copyOf(visible);
        hidden = List.copyOf(hidden);
    }

    public static <T> RowSet<T> empty() {
        return new RowSet<>(List.of(), List.of());
    }

    public int total() {
        return visible.size() + hidden.size();
    }

    /** Cells the visible rows add to the main area. */
    public int visibleCells() {
        return visible.size() * ExtensionCategories.ROW_CELLS;
    }

    public int visibleOf(String category) {
        return count(visible, category);
    }

    public int hiddenOf(String category) {
        return count(hidden, category);
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

    /** The categories of the visible rows, in order: what the layout of the main area depends on. */
    public List<String> visibleCategories() {
        List<String> out = new ArrayList<>(visible.size());
        for (Row<T> row : visible) {
            out.add(row.category());
        }
        return out;
    }

    /** Per category (in the fixed order, unknown ids last): the cells holding something in the hidden rows. */
    public List<HiddenSummary> hiddenSummary(Predicate<T> isEmpty) {
        List<String> categories = new ArrayList<>();
        for (Row<T> row : hidden) {
            if (!categories.contains(row.category())) {
                categories.add(row.category());
            }
        }
        categories.sort((a, b) -> Integer.compare(ExtensionCategories.orderOf(a), ExtensionCategories.orderOf(b)));
        List<HiddenSummary> out = new ArrayList<>();
        for (String category : categories) {
            int cells = 0;
            for (Row<T> row : hidden) {
                if (row.category().equals(category)) {
                    for (T cell : row.cells()) {
                        if (!isEmpty.test(cell)) {
                            cells++;
                        }
                    }
                }
            }
            out.add(new HiddenSummary(category, cells));
        }
        return out;
    }

    /** Whether any hidden row holds something. */
    public boolean hasHiddenItems(Predicate<T> isEmpty) {
        for (Row<T> row : hidden) {
            for (T cell : row.cells()) {
                if (!isEmpty.test(cell)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Foreign data made safe: every row cut or padded to nine cells, category ids cut to
     * {@link ExtensionCategories#MAX_CATEGORY_LENGTH}, empty hidden rows dropped, and the rows cut to
     * {@link ExtensionCategories#MAX_ROWS} in all (the end of the hidden rows first, then the end of the visible ones).
     */
    public static <T> RowSet<T> sanitize(
            List<Row<T>> visible, List<Row<T>> hidden, Predicate<T> isEmpty, Supplier<T> emptyCell) {
        List<Row<T>> v = new ArrayList<>();
        for (Row<T> row : visible == null ? List.<Row<T>>of() : visible) {
            if (row != null) {
                v.add(fit(row, emptyCell));
            }
        }
        List<Row<T>> h = new ArrayList<>();
        for (Row<T> row : hidden == null ? List.<Row<T>>of() : hidden) {
            if (row != null) {
                Row<T> fitted = fit(row, emptyCell);
                if (!allEmpty(fitted, isEmpty)) {
                    h.add(fitted);
                }
            }
        }
        while (v.size() + h.size() > ExtensionCategories.MAX_ROWS) {
            if (!h.isEmpty()) {
                h.remove(h.size() - 1);
            } else {
                v.remove(v.size() - 1);
            }
        }
        return new RowSet<>(v, h);
    }

    private static <T> boolean allEmpty(Row<T> row, Predicate<T> isEmpty) {
        for (T cell : row.cells()) {
            if (!isEmpty.test(cell)) {
                return false;
            }
        }
        return true;
    }

    private static <T> Row<T> fit(Row<T> row, Supplier<T> emptyCell) {
        String category = row.category().length() > ExtensionCategories.MAX_CATEGORY_LENGTH
                ? row.category().substring(0, ExtensionCategories.MAX_CATEGORY_LENGTH)
                : row.category();
        List<T> cells = new ArrayList<>(ExtensionCategories.ROW_CELLS);
        for (int i = 0; i < ExtensionCategories.ROW_CELLS; i++) {
            cells.add(i < row.cells().size() ? row.cells().get(i) : emptyCell.get());
        }
        return new Row<>(category, cells);
    }
}
