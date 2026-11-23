package pnnh.mcfloatingbackpack.compat.ae2;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.helpers.InventoryAction;
import appeng.menu.me.common.MEStorageMenu;

import pnnh.mcfloatingbackpack.compat.NetworkStorageBridge;
import pnnh.mcfloatingbackpack.compat.ThirdPartyTable;

/**
 * 060: takes items out of the ME network of an AE2 terminal for the floating backpack, using the menu's own "pick up / set down" of an
 * entry: with an empty cursor AE2 itself checks the power and the permissions, takes up to a full stack out of the network and puts it on
 * the menu's carried stack; with something carried it puts that back into the network. Here the carried stack is only the way between
 * the network and the backpack: it is read and emptied at once. Nothing of AE2's power, security or storage types is touched.
 *
 * <p>Storing into an ME terminal is not done here ({@link #handles} is always false): the generic path of 012 does that, and it was
 * verified. This class and {@link Ae2Client} and {@link Ae2Compat} are the only code that touches AE2 types (constitution VII); they are
 * never loaded when AE2 is not.
 */
final class Ae2Bridge implements NetworkStorageBridge {
    @Override
    public String modId() {
        return ThirdPartyTable.AE2;
    }

    @Override
    public boolean handles(AbstractContainerMenu menu) {
        return false;
    }

    @Override
    public ItemStack insert(ServerPlayer player, AbstractContainerMenu menu, ItemStack stack) {
        throw new UnsupportedOperationException("storing into an ME terminal is left to the generic path");
    }

    @Override
    public boolean handlesTake(AbstractContainerMenu menu) {
        return menu instanceof MEStorageMenu;
    }

    @Override
    public ItemStack take(ServerPlayer player, AbstractContainerMenu menu, ItemStack what, long handle) {
        MEStorageMenu terminal = (MEStorageMenu) menu;
        if (!terminal.getCarried().isEmpty()) {
            return ItemStack.EMPTY;
        }
        terminal.handleInteraction(handle, InventoryAction.PICKUP_OR_SET_DOWN);
        ItemStack taken = terminal.getCarried().copy();
        terminal.setCarried(ItemStack.EMPTY);
        return taken;
    }

    @Override
    public ItemStack giveBack(ServerPlayer player, AbstractContainerMenu menu, ItemStack stack, long handle) {
        MEStorageMenu terminal = (MEStorageMenu) menu;
        if (!terminal.getCarried().isEmpty()) {
            return stack;
        }
        terminal.setCarried(stack.copy());
        terminal.handleInteraction(handle, InventoryAction.PICKUP_OR_SET_DOWN);
        ItemStack rest = terminal.getCarried().copy();
        terminal.setCarried(ItemStack.EMPTY);
        return rest;
    }
}
