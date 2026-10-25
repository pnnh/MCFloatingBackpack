package pnnh.mcfloatingbackpack.client.panel;

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
    private int visibleRows = 4;
    private int scrollRow;
    private boolean scrolling;
    private int scrollGrabOffsetY;

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
            scrolling = false;
            scrollRow = 0;
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

    public boolean isScrolling() {
        return scrolling;
    }

    public void beginDrag(int mouseX, int mouseY) {
        dragging = true;
        scrolling = false;
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

    public void beginScrollDrag(int mouseY) {
        scrolling = true;
        dragging = false;
        scrollGrabOffsetY = mouseY - PanelSlots.scrollbarThumbY(this);
    }

    public void updateScrollDrag(int mouseY) {
        if (!scrolling) {
            return;
        }
        int thumbTop = mouseY - scrollGrabOffsetY;
        int trackY = PanelSlots.scrollbarTrackY(this);
        int track = PanelSlots.scrollbarTrackHeight(this);
        int thumb = PanelSlots.scrollbarThumbHeight(this);
        int travel = Math.max(1, track - thumb);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        if (maxScroll <= 0) {
            scrollRow = 0;
            return;
        }
        double ratio = (thumbTop - trackY) / (double) travel;
        setScrollRow((int) Math.round(ratio * maxScroll));
    }

    public void endScrollDrag() {
        scrolling = false;
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

    public boolean needsScrollbar() {
        return totalRows > visibleRows;
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

    /**
     * Fits visible rows to the screen under the current panel top, leaving HUD margin at the
     * bottom. Widens the chrome when a scrollbar is required.
     */
    public void layout(int screenHeight) {
        int bottomLimit = Math.max(PanelSlots.panelHeight(1), screenHeight - BOTTOM_HUD_MARGIN);
        int available = bottomLimit - posY - PanelSlots.PAD_TOP - PanelSlots.PAD_BOTTOM;
        if (available < PanelSlots.SLOT_SIZE) {
            int maxFit = Math.max(1, (bottomLimit - PanelSlots.PAD_TOP - PanelSlots.PAD_BOTTOM)
                    / PanelSlots.SLOT_SIZE);
            int targetRows = Math.min(totalRows, maxFit);
            int needed = PanelSlots.panelHeight(targetRows);
            posY = Math.max(0, bottomLimit - needed);
            available = bottomLimit - posY - PanelSlots.PAD_TOP - PanelSlots.PAD_BOTTOM;
        }
        int fit = Math.max(1, available / PanelSlots.SLOT_SIZE);
        visibleRows = Math.min(totalRows, fit);
        clampScroll();
        width = PanelSlots.panelWidth(needsScrollbar());
        height = PanelSlots.panelHeight(visibleRows);
        if (posY + height > bottomLimit) {
            posY = Math.max(0, bottomLimit - height);
        }
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, totalRows - Math.max(1, visibleRows));
        if (scrollRow > maxScroll) {
            scrollRow = maxScroll;
        }
        if (scrollRow < 0) {
            scrollRow = 0;
        }
    }
}
