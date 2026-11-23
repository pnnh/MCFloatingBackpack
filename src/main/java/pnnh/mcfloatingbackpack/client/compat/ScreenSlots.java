package pnnh.mcfloatingbackpack.client.compat;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** 060: finding the slot of a container screen under the cursor by our own hit test (the game's own is only refreshed when it draws). */
public final class ScreenSlots {
    private ScreenSlots() {
    }

    /** The first active slot of the screen's menu that lies under the cursor, or null. */
    public static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            int left = screen.getGuiLeft() + slot.x;
            int top = screen.getGuiTop() + slot.y;
            if (mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 16) {
                return slot;
            }
        }
        return null;
    }
}
