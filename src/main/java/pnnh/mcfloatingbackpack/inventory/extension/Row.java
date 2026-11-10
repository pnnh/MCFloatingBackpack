package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.List;

/**
 * 035: one row of a category extension: a category and its cells (nine of them once {@link RowSet#sanitize} has seen
 * it). The category may be an id this version does not know; the row is kept all the same, so no item is ever lost.
 */
public record Row<T>(String category, List<T> cells) {
    public Row {
        category = category == null ? "" : category;
        cells = List.copyOf(cells);
    }
}
