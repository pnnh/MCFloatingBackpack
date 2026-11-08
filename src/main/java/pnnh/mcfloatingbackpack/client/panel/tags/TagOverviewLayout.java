package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.ArrayList;
import java.util.List;

import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Rect;

/**
 * 025: where everything on the overview list goes. Coordinates are relative to the panel's top-left corner (the main area
 * the panel covers). From the top: the status row (title on the left, the menu icon at the right end), the filter row,
 * then one row of {@link #ROW_H} per category (icon, name, tag count and a clear button at the right end); while the
 * filter box is being edited its drop-down lies over the rows. The rows scroll by whole rows. Pure arithmetic with no
 * Minecraft types: the width of the clear button comes from the caller.
 */
public final class TagOverviewLayout {
    public static final int ROW_H = 18;
    public static final int FILTER_H = 14;
    public static final int PAD = 2;
    public static final int ICON = 14;
    public static final int ROW_ICON = 16;
    public static final int COUNT_W = 14;
    public static final int DROPDOWN_H = 14;
    public static final int DROPDOWN_MAX = 5;
    private static final int CLEAR_MARK_W = 12;

    public enum Kind {
        MENU,
        FILTER,
        FILTER_CLEAR,
        DROPDOWN,
        ROW,
        CLEAR,
        NONE
    }

    /** What is at a point; for a row or a drop-down entry the index is the row's index in the whole list / the entry's. */
    public record Hit(Kind kind, int index) {
    }

    private final int width;
    private final int height;
    private final int clearWidth;
    private final int rowCount;
    private final int listTop;
    private final int visibleRows;
    private final int maxScroll;
    private final int scroll;
    private final Rect filter;
    private final Rect filterClear;
    private final List<Rect> dropdown = new ArrayList<>();

    private TagOverviewLayout(
            int width,
            int height,
            int rowCount,
            int scroll,
            boolean filterHasValue,
            boolean filterEditing,
            int dropdownCount,
            int clearWidth) {
        this.width = width;
        this.height = height;
        this.clearWidth = clearWidth;
        this.rowCount = Math.max(0, rowCount);
        int filterY = TagPanelLayout.ROW + PAD;
        this.filter = new Rect(PAD, filterY, Math.max(0, width - 2 * PAD), FILTER_H);
        this.filterClear = filterHasValue && !filterEditing
                ? new Rect(filter.x() + filter.w() - CLEAR_MARK_W - 1, filterY + 1, CLEAR_MARK_W, FILTER_H - 2)
                : null;
        this.listTop = filterY + FILTER_H + PAD;
        this.visibleRows = Math.max(0, (height - listTop) / ROW_H);
        this.maxScroll = Math.max(0, this.rowCount - visibleRows);
        this.scroll = Math.min(Math.max(0, scroll), maxScroll);
        if (filterEditing) {
            int y = filterY + FILTER_H + 1;
            for (int i = 0; i < Math.min(Math.max(0, dropdownCount), DROPDOWN_MAX); i++) {
                dropdown.add(new Rect(PAD, y, filter.w(), DROPDOWN_H));
                y += DROPDOWN_H + 1;
            }
        }
    }

    /** The menu icon at the right end of the status row; the selection view's tag panel draws and hits it too. */
    public static Rect menuIconRect(int width) {
        return new Rect(width - PAD - ICON, PAD, ICON, ICON);
    }

    /**
     * @param rowCount how many categories the list has (after the filter)
     * @param scroll rows scrolled (clamped to the possible range)
     * @param clearWidth the width of every row's clear button
     */
    public static TagOverviewLayout of(
            int width,
            int height,
            int rowCount,
            int scroll,
            boolean filterHasValue,
            boolean filterEditing,
            int dropdownCount,
            int clearWidth) {
        return new TagOverviewLayout(width, height, rowCount, scroll, filterHasValue, filterEditing, dropdownCount, clearWidth);
    }

    public Rect filterRect() {
        return filter;
    }

    /** The mark that clears the filter, or null when no filter is set or the box is being edited. */
    public Rect filterClearRect() {
        return filterClear;
    }

    public int dropdownCount() {
        return dropdown.size();
    }

    public Rect dropdownRect(int index) {
        return dropdown.get(index);
    }

    /** How many whole rows fit. */
    public int visibleRows() {
        return visibleRows;
    }

    /** The scroll after clamping. */
    public int scroll() {
        return scroll;
    }

    public int maxScroll() {
        return maxScroll;
    }

    /** The index (in the whole list) of the first row shown. */
    public int firstVisible() {
        return scroll;
    }

    /** How many rows are shown right now. */
    public int visibleCount() {
        return Math.max(0, Math.min(visibleRows, rowCount - scroll));
    }

    /** The row shown at screen position {@code i} (0 = the first one shown). */
    public Rect rowRect(int i) {
        return new Rect(PAD, listTop + i * ROW_H, Math.max(0, width - 2 * PAD), ROW_H);
    }

    public Rect iconRect(int i) {
        Rect row = rowRect(i);
        return new Rect(row.x() + 1, row.y() + 1, ROW_ICON, ROW_ICON);
    }

    public Rect clearRect(int i) {
        Rect row = rowRect(i);
        int w = Math.min(clearWidth, row.w());
        return new Rect(row.x() + row.w() - w, row.y() + 2, w, ROW_H - 4);
    }

    public Rect countRect(int i) {
        Rect row = rowRect(i);
        Rect clear = clearRect(i);
        int x = Math.max(row.x(), clear.x() - COUNT_W - 2);
        return new Rect(x, row.y(), Math.max(0, clear.x() - 2 - x), ROW_H);
    }

    public Rect nameRect(int i) {
        Rect row = rowRect(i);
        Rect icon = iconRect(i);
        int x = icon.x() + icon.w() + 3;
        Rect count = countRect(i);
        return new Rect(x, row.y(), Math.max(0, count.x() - 2 - x), ROW_H);
    }

    /** What is at a point (panel-relative); only rows shown can be hit. */
    public Hit hit(double x, double y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return new Hit(Kind.NONE, 0);
        }
        if (y < TagPanelLayout.ROW) {
            return menuIconRect(width).contains(x, y) ? new Hit(Kind.MENU, 0) : new Hit(Kind.NONE, 0);
        }
        for (int i = 0; i < dropdown.size(); i++) {
            if (dropdown.get(i).contains(x, y)) {
                return new Hit(Kind.DROPDOWN, i);
            }
        }
        if (filterClear != null && filterClear.contains(x, y)) {
            return new Hit(Kind.FILTER_CLEAR, 0);
        }
        if (filter.contains(x, y)) {
            return new Hit(Kind.FILTER, 0);
        }
        for (int i = 0; i < visibleCount(); i++) {
            if (clearRect(i).contains(x, y)) {
                return new Hit(Kind.CLEAR, scroll + i);
            }
            if (rowRect(i).contains(x, y)) {
                return new Hit(Kind.ROW, scroll + i);
            }
        }
        return new Hit(Kind.NONE, 0);
    }
}
