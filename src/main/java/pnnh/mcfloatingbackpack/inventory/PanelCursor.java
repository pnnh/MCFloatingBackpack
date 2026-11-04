package pnnh.mcfloatingbackpack.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Where the stack the player holds lives (013). With a container screen open it is the menu's carried
 * stack; otherwise it is the panel's own cursor inside the backpack's contents. Same rule as the existing
 * panel click ({@code BackpackClickPayload}): {@code screenOpen} is reported by the client.
 */
public final class PanelCursor {
    private final AbstractContainerMenu menu; // null => panel cursor
    private final BackpackInventory backpack;

    private PanelCursor(AbstractContainerMenu menu, BackpackInventory backpack) {
        this.menu = menu;
        this.backpack = backpack;
    }

    public static PanelCursor of(boolean screenOpen, AbstractContainerMenu menu, BackpackInventory backpack) {
        return new PanelCursor(screenOpen && menu != null ? menu : null, backpack);
    }

    public boolean usesMenu() {
        return menu != null;
    }

    public ItemStack get() {
        return menu != null ? menu.getCarried() : backpack.getCursor();
    }

    public void set(ItemStack stack) {
        ItemStack value = stack.isEmpty() ? ItemStack.EMPTY : stack;
        if (menu != null) {
            menu.setCarried(value);
            backpack.setCursor(ItemStack.EMPTY);
        } else {
            backpack.setCursor(value);
        }
    }
}
