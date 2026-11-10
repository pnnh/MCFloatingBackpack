package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.List;

/**
 * 037: which row of the category extensions each cell of the main area belongs to, for the panel to colour, separate and explain
 * them. The main area is a flat list the panel wraps over however many columns it shows: the cells of the tier capacity first,
 * then nine cells per visible row in the order of the rows (035). So a row is only a range of indices, and everything here is
 * arithmetic on the index; nothing depends on the columns except the two edge questions, which compare a cell with the one
 * above it and the one to its left.
 *
 * <p>Immutable, pure logic with no Minecraft types.
 */
public final class RowLayout {
    /** The cell is one of the tier capacity's cells (the ordinary grid). */
    public static final int MAIN = -1;
    /** The index is in no visible row (and not in the tier capacity). */
    public static final int NONE = -2;

    private final int baseCapacity;
    private final List<String> visibleCategories;

    public RowLayout(int baseCapacity, List<String> visibleCategories) {
        this.baseCapacity = Math.max(0, baseCapacity);
        this.visibleCategories = List.copyOf(visibleCategories);
    }

    public boolean hasRows() {
        return !visibleCategories.isEmpty();
    }

    public int baseCapacity() {
        return baseCapacity;
    }

    /** {@link #MAIN}, the number of the visible row the index is in, or {@link #NONE}. */
    public int groupOf(int index) {
        if (index < 0) {
            return NONE;
        }
        if (index < baseCapacity) {
            return MAIN;
        }
        int row = (index - baseCapacity) / ExtensionCategories.ROW_CELLS;
        return row < visibleCategories.size() ? row : NONE;
    }

    /** The category of the row the index is in; null for the tier capacity's cells and for an index in no row. */
    public String categoryOf(int index) {
        int group = groupOf(index);
        return group >= 0 ? visibleCategories.get(group) : null;
    }

    /** Whether the index is the first cell of a row. */
    public boolean isRowStart(int index) {
        return groupOf(index) >= 0 && (index - baseCapacity) % ExtensionCategories.ROW_CELLS == 0;
    }

    /** Whether a separator is drawn on the top edge of the cell: the cell above it is in another group. Only cells of a row have one. */
    public boolean topEdge(int index, int cols) {
        int group = groupOf(index);
        if (group < 0) {
            return false;
        }
        int above = index - Math.max(1, cols);
        return above < 0 || groupOf(above) != group;
    }

    /** Whether a separator is drawn on the left edge of the cell: it is not at the left of its line and the cell before it is in another group. */
    public boolean leftEdge(int index, int cols) {
        int group = groupOf(index);
        if (group < 0) {
            return false;
        }
        int c = Math.max(1, cols);
        return index % c != 0 && groupOf(index - 1) != group;
    }

    /**
     * 039: whether the index is in a visible row whose category this version does not know (a row made by a newer version). The panel
     * guesses generously for such a row and leaves the decision to the server.
     */
    public boolean isUnknownRow(int index) {
        int group = groupOf(index);
        return group >= 0 && !ExtensionCategories.isKnown(visibleCategories.get(group));
    }
}

