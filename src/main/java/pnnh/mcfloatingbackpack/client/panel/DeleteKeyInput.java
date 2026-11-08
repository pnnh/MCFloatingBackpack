package pnnh.mcfloatingbackpack.client.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.DeleteKeyRules.Inputs;
import pnnh.mcfloatingbackpack.client.panel.DeleteKeyRules.ScreenKind;
import pnnh.mcfloatingbackpack.client.panel.DeleteKeyRules.Target;
import pnnh.mcfloatingbackpack.client.panel.pickup.PickupPanelState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashActions;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmState;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.inventory.trash.DeletableSlots;

/**
 * 022: the part of the delete key that talks to the game: gathering what {@link DeleteKeyRules} needs to know (the
 * screen, the selection, the slot under the mouse), and carrying out the target it names. Called from the
 * {@code ScreenEvent.KeyPressed.Post} hook, which only fires for a key the screen itself did not handle (a search box
 * that has the focus has already taken a Backspace), and only for the mapping's own key.
 */
@OnlyIn(Dist.CLIENT)
public final class DeleteKeyInput {
    private DeleteKeyInput() {
    }

    /**
     * @param mouseX the mouse position in GUI pixels
     * @return true if the key was used and must be swallowed; false leaves it to everyone else
     */
    public static boolean onKey(Screen screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        FloatingPanelState panel = FloatingPanelState.get();
        ClientBackpackView view = ClientBackpackView.get();
        ScreenKind kind = kindOf(screen);

        int hoverMain = PanelSlots.indexAt(panel, mouseX, mouseY);
        boolean hoverMainHasItem = hoverMain >= 0
                && hoverMain < view.slotCount()
                && !view.getStackInSlot(hoverMain).isEmpty();
        Slot menuSlot = menuSlotUnderMouse(screen, kind);
        boolean menuOk = menuSlot != null
                && menuSlot.hasItem()
                && DeletableSlots.allowed(
                        menuSlot.container == minecraft.player.getInventory(),
                        menuSlot.getContainerSlot(),
                        screen instanceof AbstractContainerScreen<?> container
                                && container.getMenu() instanceof ChestMenu chest
                                && menuSlot.container == chest.getContainer());

        Target target = DeleteKeyRules.decide(new Inputs(
                panel.isVisible(),
                view.hasTrashCan(),
                TrashConfirmState.get().isPending(),
                // 024: the pickup blacklist page covers the main area like the tag panel, and has a text box of its own.
                TagPanelState.get().isOpen() || PickupPanelState.get().isOpen(),
                TagPanelState.get().isEditing() || PickupPanelState.get().isEditing() || TagOverviewState.get().isFilterEditing(),
                kind,
                TrashActions.deleteEnabled(),
                hoverMainHasItem,
                menuOk));
        switch (target) {
            case MAIN_SELECTION -> TrashActions.deleteSelection();
            case MAIN_HOVER -> TrashActions.deleteMainSlots(new int[] {hoverMain});
            case MENU_SLOT -> {
                AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
                TrashActions.deleteMenuSlot(container.getMenu().containerId, menuSlot.index);
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    private static ScreenKind kindOf(Screen screen) {
        if (screen instanceof PanelHostScreen) {
            return ScreenKind.HOST;
        }
        if (screen instanceof InventoryScreen) {
            return ScreenKind.INVENTORY;
        }
        if (screen instanceof AbstractContainerScreen<?> container
                && container.getMenu() instanceof ChestMenu
                && "minecraft".equals(MenuOwner.ownerOf(container.getMenu()))) {
            return ScreenKind.CHEST;
        }
        return ScreenKind.OTHER;
    }

    private static Slot menuSlotUnderMouse(Screen screen, ScreenKind kind) {
        if ((kind == ScreenKind.INVENTORY || kind == ScreenKind.CHEST) && screen instanceof AbstractContainerScreen<?> container) {
            return container.getSlotUnderMouse();
        }
        return null;
    }
}
