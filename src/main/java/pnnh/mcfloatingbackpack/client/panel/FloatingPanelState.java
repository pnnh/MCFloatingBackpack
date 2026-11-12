package pnnh.mcfloatingbackpack.client.panel;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * Client runtime state for the empty floating panel. No item data.
 * Kept free of Dist annotations so clamp logic can be unit-tested without NeoForge.
 */
public final class FloatingPanelState {
    public static final int DEFAULT_WIDTH = PanelSlots.panelWidth();
    public static final int DEFAULT_HEIGHT = PanelSlots.panelHeight(4);
    public static final int DRAG_BAR_HEIGHT = 14;
    public static final int DEFAULT_POS_X = 20;
    public static final int DEFAULT_POS_Y = 20;
    /** Leave room for hearts / hunger / XP / hotbar under the panel. */
    public static final int BOTTOM_HUD_MARGIN = 40;
    /** Lower bound for both player-adjustable dimensions (007). */
    public static final int MIN_VISIBLE_CELLS = 3;
    /** Pixel hit-box square at the bottom-right corner used to drag-resize the panel. */
    public static final int RESIZE_HANDLE_SIZE = 8;
    /** Pixel hit-box square near the top-right of the drag bar used to close the panel. */
    public static final int CLOSE_BUTTON_SIZE = 10;
    /** Pixel hit-box square at the top-left of the drag bar showing the backpack-tier icon. */
    public static final int TITLE_ICON_SIZE = 12;

    private static final FloatingPanelState INSTANCE = new FloatingPanelState();

    private boolean visible;
    private int posX = DEFAULT_POS_X;
    private int posY = DEFAULT_POS_Y;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int totalRows = 4;
    /** 042: see {@link #getSearchSlots()}. */
    private int[] searchSlots;
    private int visibleRows = 4;
    private int visibleCols = PanelSlots.COLS;
    private int scrollRow;
    /** Scroll offsets of the three tool areas (016), indexed by {@link PanelArea#ordinal()}; MAIN uses {@link #scrollRow}. */
    private final int[] toolScroll = new int[PanelArea.values().length];
    /** Slot counts of the three tool areas, as last reported by the client view; used only to clamp their scrolling. */
    private final int[] toolCount = new int[PanelArea.values().length];
    /** Main slot count, as last reported by the client view; 0 until the first sync (017: used to decide which sizes are valid). */
    private int mainCount;
    /**
     * True once the player has explicitly resized the panel (by dragging the handle, or by
     * loading a previously-saved size). While false, {@link #layout(int, int)} keeps the old
     * "auto-fit to available space" behavior instead of respecting {@link #visibleRows}/
     * {@link #visibleCols} as a sticky preference.
     */
    private boolean sizePreferenceSet;
    private boolean resizing;
    private int resizeGrabOffsetX;
    private int resizeGrabOffsetY;
    private int resizeStartCols;
    private int resizeStartRows;

    private FloatingPanelState() {
    }

    public static FloatingPanelState get() {
        return INSTANCE;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            dragging = false;
            resizing = false;
            scrollRow = 0;
            java.util.Arrays.fill(toolScroll, 0);
        }
    }

    public void toggleVisible() {
        setVisible(!visible);
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }

    public void setPos(int posX, int posY) {
        this.posX = posX;
        this.posY = posY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isResizing() {
        return resizing;
    }

    public int getVisibleCols() {
        return visibleCols;
    }

    public boolean isSizePreferenceSet() {
        return sizePreferenceSet;
    }

    /** Clears any saved/dragged size preference, reverting to auto-fit on the next layout(). */
    public void clearSizePreference() {
        sizePreferenceSet = false;
    }

    /** Used when loading a persisted size from {@code FloatingPanelConfig}. */
    public void setSizePreference(int visibleRows, int visibleCols) {
        this.visibleRows = Math.max(MIN_VISIBLE_CELLS, visibleRows);
        this.visibleCols = Math.max(MIN_VISIBLE_CELLS, visibleCols);
        this.sizePreferenceSet = true;
        clampScroll();
    }

    public void beginDrag(int mouseX, int mouseY) {
        dragging = true;
        resizing = false;
        dragOffsetX = mouseX - posX;
        dragOffsetY = mouseY - posY;
    }

    public void updateDrag(int mouseX, int mouseY) {
        if (!dragging) {
            return;
        }
        posX = mouseX - dragOffsetX;
        posY = mouseY - dragOffsetY;
    }

    public void endDrag() {
        dragging = false;
    }

    /**
     * Starts a drag-resize gesture. The resulting row/column count is recorded relative to this
     * grab point ({@link #updateResize}), not accumulated per mouse-move event, so the gesture
     * doesn't drift from integer rounding.
     */
    public void beginResize(int mouseX, int mouseY) {
        resizing = true;
        dragging = false;
        sizePreferenceSet = true;
        resizeGrabOffsetX = mouseX;
        resizeGrabOffsetY = mouseY;
        resizeStartCols = visibleCols;
        resizeStartRows = visibleRows;
    }

    public void updateResize(int mouseX, int mouseY) {
        if (!resizing) {
            return;
        }
        int deltaCols = Math.floorDiv(
                (mouseX - resizeGrabOffsetX) + PanelSlots.SLOT_SIZE / 2, PanelSlots.SLOT_SIZE);
        int deltaRows = Math.floorDiv(
                (mouseY - resizeGrabOffsetY) + PanelSlots.SLOT_SIZE / 2, PanelSlots.SLOT_SIZE);
        int targetCols = clamp(resizeStartCols + deltaCols, MIN_VISIBLE_CELLS, PanelSlots.COLS);
        int targetRows = clamp(
                resizeStartRows + deltaRows, MIN_VISIBLE_CELLS, Math.max(MIN_VISIBLE_CELLS, totalRows));
        // 017: a target that would show a solid filler cell is ignored, not adjusted: the size stays at the last
        // valid one until the drag reaches a valid target again. Columns and rows are judged on their own, and
        // the rows against the columns that were just adopted (the row limit depends on them).
        PanelShape.Counts counts = counts();
        if (PanelShape.isValidCols(counts, targetCols)) {
            visibleCols = targetCols;
        }
        if (PanelShape.isValidRows(counts, visibleCols, targetRows)) {
            visibleRows = targetRows;
        }
        clampScroll();
    }

    public void endResize() {
        resizing = false;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= posX && mouseX < posX + width && mouseY >= posY && mouseY < posY + height;
    }

    public boolean hitsDragBar(double mouseX, double mouseY) {
        return mouseX >= posX && mouseX < posX + width && mouseY >= posY && mouseY < posY + DRAG_BAR_HEIGHT;
    }

    public boolean hitsBody(double mouseX, double mouseY) {
        return contains(mouseX, mouseY) && !hitsDragBar(mouseX, mouseY);
    }

    /** Fixed-size grip at the panel's bottom-right corner; drag it to resize. */
    public boolean hitsResizeHandle(double mouseX, double mouseY) {
        int x2 = posX + width;
        int x1 = x2 - RESIZE_HANDLE_SIZE;
        int y2 = posY + height;
        int y1 = y2 - RESIZE_HANDLE_SIZE;
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    /** 042: the close button is left out at the smallest size (3 main columns): B and Esc close the panel as well. */
    public boolean showClose() {
        return visibleCols > MIN_VISIBLE_CELLS;
    }

    /** Fixed-size close control near the top-right of the drag bar; there is none at the smallest size. */
    public boolean hitsCloseButton(double mouseX, double mouseY) {
        if (!showClose()) {
            return false;
        }
        int x1 = titleBar().closeLeft();
        int y1 = posY + 2;
        return mouseX >= x1
                && mouseX < x1 + CLOSE_BUTTON_SIZE
                && mouseY >= y1
                && mouseY < y1 + CLOSE_BUTTON_SIZE;
    }

    /** Fixed-size backpack-tier icon at the top-left of the drag bar. */
    public boolean hitsTitleIcon(double mouseX, double mouseY) {
        int x1 = posX + 4;
        int x2 = x1 + TITLE_ICON_SIZE;
        int y1 = posY + 1;
        int y2 = y1 + TITLE_ICON_SIZE;
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    /** 026: the left edge of the close button (042: meaningless when {@link #showClose()} is false). */
    public int closeButtonLeft() {
        return titleBar().closeLeft();
    }

    /** 042: where the title bar's pieces go at the panel's current position and width (the close button is left out at 3 columns). */
    public TitleBarLayout titleBar() {
        return TitleBarLayout.of(posX, width, showClose());
    }

    /** 031: the snapshot button; never hit when the bar is too narrow to show it. */
    public boolean hitsSnapshotButton(double mouseX, double mouseY) {
        TitleBarLayout bar = titleBar();
        return bar.hasSnapshot() && hitsButton(bar.snapshotLeft(), mouseX, mouseY);
    }

    /** 042: the magnifier, left of the snapshot button (only drawn and hit while the search box is closed: the caller checks). */
    public boolean hitsSearchButton(double mouseX, double mouseY) {
        return hitsButton(titleBar().searchLeft(), mouseX, mouseY);
    }

    private boolean hitsButton(int left, double mouseX, double mouseY) {
        int y1 = posY + 2;
        return mouseX >= left
                && mouseX < left + TitleBarLayout.BUTTON_SIZE
                && mouseY >= y1
                && mouseY < y1 + TitleBarLayout.BUTTON_SIZE;
    }

    /** 042: the open search box: from the tier icon to the close button (or the right end), as tall as the buttons. */
    public boolean hitsSearchBox(double mouseX, double mouseY) {
        TitleBarLayout bar = titleBar();
        int y1 = posY + 2;
        return mouseX >= bar.boxLeft()
                && mouseX < bar.boxRight()
                && mouseY >= y1
                && mouseY < y1 + TitleBarLayout.BUTTON_SIZE;
    }

    /** 042: the clear button inside the open search box. */
    public boolean hitsSearchClear(double mouseX, double mouseY) {
        TitleBarLayout bar = titleBar();
        int left = bar.boxRight() - SearchBoxLayout.RIGHT_INSET - SearchBoxLayout.CLEAR_SIZE;
        int y1 = posY + 2;
        return mouseX >= left
                && mouseX < left + SearchBoxLayout.CLEAR_SIZE
                && mouseY >= y1
                && mouseY < y1 + TitleBarLayout.BUTTON_SIZE;
    }

    /** 026: the backpack's number in the title bar, to the right of the tier icon ({@code textWidth}: the whole number's width). */
    public boolean hitsTitleId(double mouseX, double mouseY, int textWidth) {
        TitleIdLayout layout = TitleIdLayout.of(posX, titleBar().textRightLimit(), textWidth);
        int x1 = layout.x();
        int x2 = x1 + Math.min(textWidth, layout.maxWidth());
        int y1 = posY + 1;
        int y2 = y1 + TITLE_ICON_SIZE;
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    /**
     * Keeps at least the drag bar inside the GUI viewport so the panel cannot be lost off-screen.
     */
    public static void clampToScreen(FloatingPanelState state, int screenWidth, int screenHeight) {
        int maxX = Math.max(0, screenWidth - state.width);
        int maxY = Math.max(0, screenHeight - DRAG_BAR_HEIGHT);
        state.posX = Math.min(Math.max(0, state.posX), maxX);
        state.posY = Math.min(Math.max(0, state.posY), maxY);
    }

    public void clampToScreen(int screenWidth, int screenHeight) {
        clampToScreen(this, screenWidth, screenHeight);
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public int getScrollRow() {
        return scrollRow;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = Math.max(1, totalRows);
        clampScroll();
    }

    public void setScrollRow(int scrollRow) {
        this.scrollRow = Math.max(0, scrollRow);
        clampScroll();
    }

    public void scrollBy(int deltaRows) {
        setScrollRow(scrollRow + deltaRows);
    }

    public int getScroll(PanelArea area) {
        return area == PanelArea.MAIN ? scrollRow : toolScroll[area.ordinal()];
    }

    /** Scrolls one area on its own: rows for the main area and the side columns, columns for the bottom row. */
    public void scrollBy(PanelArea area, int delta) {
        if (area == PanelArea.MAIN) {
            scrollBy(delta);
            return;
        }
        toolScroll[area.ordinal()] = Math.max(0, toolScroll[area.ordinal()] + delta);
        clampScroll();
    }

    /** Tells the state how many main slots the backpack has (017); only the valid panel sizes depend on it. */
    public void setMainCount(int count) {
        mainCount = Math.max(0, count);
    }

    /** The slot counts of the four areas as last reported; the input to {@link PanelShape}. */
    public PanelShape.Counts counts() {
        return new PanelShape.Counts(
                mainCount,
                toolCount[PanelArea.LEFT.ordinal()],
                toolCount[PanelArea.RIGHT.ordinal()],
                toolCount[PanelArea.BOTTOM.ordinal()]);
    }

    /**
     * Tells the state how many slots a tool area has so its scrolling can be clamped. A different count
     * means the content changed shape, so that area goes back to its start (FR-016).
     */
    public void setToolCount(PanelArea area, int count) {
        if (!area.isTool()) {
            return;
        }
        if (toolCount[area.ordinal()] != count) {
            toolCount[area.ordinal()] = count;
            toolScroll[area.ordinal()] = 0;
        }
        clampScroll();
    }

    /**
     * Before the player ever resizes the panel (and before any saved size is loaded), sizes it to
     * its content (one row per backpack row, {@link PanelSlots#COLS} columns) and only shrinks it
     * when the screen can't fit that many cells — the same budget-then-clamp approach used by
     * Traveler's Backpack and Sophisticated Backpacks. The cell count MUST NOT depend on where the
     * panel is currently dragged to, or it visibly grows/shrinks while dragging instead of staying
     * a stable size for a given screen and backpack tier.
     *
     * <p>Once the player drags the resize handle (or a saved size is loaded), {@link #visibleRows}/
     * {@link #visibleCols} become a sticky preference instead: this method only clamps them back
     * into range if the current screen can no longer fit the preferred size.
     */
    public void layout(int screenWidth, int screenHeight) {
        int bottomLimit = Math.max(PanelLayout.height(1), screenHeight - BOTTOM_HUD_MARGIN);
        // 016: the bottom row and the two side columns are always attached outside the main area, so they
        // come off the budget before the main area gets its rows and columns.
        int availableHeight = bottomLimit - PanelSlots.PAD_TOP - PanelSlots.PAD_BOTTOM;
        int fitRows = Math.max(1, availableHeight / PanelSlots.SLOT_SIZE - 1);

        int availableWidth = Math.max(PanelSlots.SLOT_SIZE, screenWidth - PanelSlots.PAD_X * 2);
        int fitCols = Math.max(1, availableWidth / PanelSlots.SLOT_SIZE - 2);

        // The MIN_VISIBLE_CELLS floor wins even on a screen too small to naturally fit it; only
        // the position gets squeezed (via clampToScreen / the posY fix-up below), not the size.
        int rowsUpper = Math.max(MIN_VISIBLE_CELLS, Math.min(totalRows, fitRows));
        int colsUpper = Math.max(MIN_VISIBLE_CELLS, Math.min(PanelSlots.COLS, fitCols));

        if (sizePreferenceSet) {
            visibleRows = clamp(visibleRows, MIN_VISIBLE_CELLS, rowsUpper);
            visibleCols = clamp(visibleCols, MIN_VISIBLE_CELLS, colsUpper);
        } else {
            visibleRows = rowsUpper;
            visibleCols = colsUpper;
        }
        // 017: whatever size came out of the preference, the auto-fit or the screen limit is moved onto a size
        // that shows no solid filler cell. Columns first: the row limit depends on them. Backpacks with no
        // valid size at all (or counts not reported yet) are left as they were.
        PanelShape.Counts counts = counts();
        if (PanelShape.restricted(counts)) {
            visibleCols = PanelShape.snapCols(counts, visibleCols);
            int rowCap = PanelShape.maxRows(counts, visibleCols);
            visibleRows = clamp(visibleRows, MIN_VISIBLE_CELLS, Math.max(MIN_VISIBLE_CELLS, Math.min(rowsUpper, rowCap)));
        }
        clampScroll();
        width = PanelSlots.panelWidth(visibleCols);
        height = PanelSlots.panelHeight(visibleRows);
        if (posY + height > bottomLimit) {
            posY = Math.max(0, bottomLimit - height);
        }
    }

    /**
     * The panel's geometry for the current position, size and scroll (016).
     *
     * @param counts slot count of each area indexed by {@link pnnh.mcfloatingbackpack.inventory.PanelArea#ordinal()}
     */
    public PanelLayout layoutModel(int[] counts) {
        return PanelLayout.of(posX, posY, visibleCols, visibleRows, counts,
                new int[] {scrollRow, toolScroll[1], toolScroll[2], toolScroll[3]}, searchSlots);
    }

    /**
     * 042: the slots the main area shows while a search is on (ascending backpack slot numbers), or null when it shows them all.
     * Only the main area's scrolling follows it; {@link #getTotalRows()} and so the panel's size and shape stay what they were.
     * The array is read, never changed.
     */
    public int[] getSearchSlots() {
        return searchSlots;
    }

    public void setSearchSlots(int[] slots) {
        this.searchSlots = slots;
        clampScroll();
    }

    /** 042: the rows the main area scrolls over: the whole backpack's, or the search results' packed over the visible columns. */
    private int mainScrollRows() {
        if (searchSlots == null) {
            return totalRows;
        }
        int cols = Math.max(1, visibleCols);
        return Math.max(1, (searchSlots.length + cols - 1) / cols);
    }

    private void clampScroll() {
        scrollRow = AxisScroll.clamp(scrollRow, mainScrollRows(), Math.max(1, visibleRows));
        int sideVisible = Math.max(1, visibleRows);
        toolScroll[PanelArea.LEFT.ordinal()] =
                AxisScroll.clamp(toolScroll[PanelArea.LEFT.ordinal()], toolCount[PanelArea.LEFT.ordinal()], sideVisible);
        toolScroll[PanelArea.RIGHT.ordinal()] =
                AxisScroll.clamp(toolScroll[PanelArea.RIGHT.ordinal()], toolCount[PanelArea.RIGHT.ordinal()], sideVisible);
        toolScroll[PanelArea.BOTTOM.ordinal()] = AxisScroll.clamp(
                toolScroll[PanelArea.BOTTOM.ordinal()], toolCount[PanelArea.BOTTOM.ordinal()], Math.max(1, visibleCols) + 2);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.min(Math.max(value, min), max);
    }
}
