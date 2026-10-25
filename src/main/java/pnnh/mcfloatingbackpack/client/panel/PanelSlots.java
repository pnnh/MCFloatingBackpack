package pnnh.mcfloatingbackpack.client.panel;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;

/**
 * Shared 9×4 slot layout for the floating panel chrome and hit-testing.
 */
public final class PanelSlots {
    public static final int SLOT_COUNT = BackpackInventory.SIZE;
    public static final int SLOT_SIZE = 18;
    public static final int COLS = 9;
    public static final int ROWS = SLOT_COUNT / COLS;
    public static final int PAD_X = 7;
    public static final int PAD_TOP = 17;
    public static final int PAD_BOTTOM = 7;

    private PanelSlots() {
    }

    public static int panelWidth() {
        return PAD_X * 2 + COLS * SLOT_SIZE;
    }

    public static int panelHeight() {
        return PAD_TOP + ROWS * SLOT_SIZE + PAD_BOTTOM;
    }

    public static int slotX(FloatingPanelState state, int index) {
        int col = index % COLS;
        return state.getPosX() + PAD_X + col * SLOT_SIZE;
    }

    public static int slotY(FloatingPanelState state, int index) {
        int row = index / COLS;
        return state.getPosY() + PAD_TOP + row * SLOT_SIZE;
    }

    /** @return slot index 0–35, or -1 if the point is not inside a slot */
    public static int indexAt(FloatingPanelState state, double mouseX, double mouseY) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = slotX(state, i);
            int y = slotY(state, i);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
