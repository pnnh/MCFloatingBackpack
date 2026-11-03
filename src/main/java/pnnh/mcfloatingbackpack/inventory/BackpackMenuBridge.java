package pnnh.mcfloatingbackpack.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Bridges floating-backpack storage with the open container menu's carried stack.
 */
public final class BackpackMenuBridge {
    private BackpackMenuBridge() {
    }

    /**
     * If the menu cursor is empty and the panel still holds a stack from M2-only mode,
     * move it onto the menu cursor so the player can place it into inventory/chest slots.
     */
    public static void mergePanelCursorIntoCarried(BackpackInventory backpack, AbstractContainerMenu menu) {
        ItemStack panelCursor = backpack.getCursor();
        if (panelCursor.isEmpty()) {
            return;
        }
        if (menu.getCarried().isEmpty()) {
            menu.setCarried(panelCursor.copy());
            backpack.setCursor(ItemStack.EMPTY);
        }
    }

    /**
     * Left/right-click a backpack slot using the menu carried stack as the cursor.
     *
     * @return the carried stack after the click
     */
    public static ItemStack clickWithMenuCarried(
            BackpackInventory backpack, AbstractContainerMenu menu, int slotIndex, boolean rightClick) {
        mergePanelCursorIntoCarried(backpack, menu);
        ItemStack newCarried = BackpackClickLogic.click(
                backpack::getStackInSlot,
                (stack, index) -> backpack.setStackInSlot(index, stack),
                menu.getCarried(),
                slotIndex,
                rightClick);
        menu.setCarried(newCarried);
        backpack.setCursor(ItemStack.EMPTY);
        return newCarried;
    }
}
