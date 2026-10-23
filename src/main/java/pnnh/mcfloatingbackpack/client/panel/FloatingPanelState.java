package pnnh.mcfloatingbackpack.client.panel;

/**
 * Client runtime state for the empty floating panel. No item data.
 * Kept free of Dist annotations so clamp logic can be unit-tested without NeoForge.
 */
public final class FloatingPanelState {
    public static final int DEFAULT_WIDTH = 176;
    public static final int DEFAULT_HEIGHT = 96;
    public static final int DRAG_BAR_HEIGHT = 14;
    public static final int DEFAULT_POS_X = 20;
    public static final int DEFAULT_POS_Y = 20;

    private static final FloatingPanelState INSTANCE = new FloatingPanelState();

    private boolean visible;
    private int posX = DEFAULT_POS_X;
    private int posY = DEFAULT_POS_Y;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

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

    public void beginDrag(int mouseX, int mouseY) {
        dragging = true;
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
}
