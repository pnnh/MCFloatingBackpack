package pnnh.mcfloatingbackpack.compat.integratedterminals;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.integrateddynamics.api.network.INetworkIngredientsChannel;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabServer;
import org.cyclops.integratedterminals.core.terminalstorage.TerminalStorageTabIngredientComponentItemStackCraftingServer;
import org.cyclops.integratedterminals.core.terminalstorage.TerminalStorageTabIngredientComponentServer;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorageBase;

import pnnh.mcfloatingbackpack.compat.NetworkStorageBridge;
import pnnh.mcfloatingbackpack.compat.ThirdPartyTable;

/**
 * 058: puts items from the floating backpack into the item network of an Integrated Terminals terminal, on its item storage page. The
 * terminal's server side does nothing for a shift click on the player's inventory there (the move is driven by packets from the terminal's
 * client, and the network's items are not slots), so the generic path of 012 does not work; this does what the terminal itself does for such
 * a click, an insert into the network's channel the terminal has selected.
 *
 * <p>060: it also takes stacks out of the same page's network ({@link #take}, {@link #giveBack}), for the Ctrl+click on an entry of the
 * terminal's list.
 *
 * <p>Only the item storage page counts: the crafting page (a subclass of the storage page's class) and the ender chest page (another class)
 * are left to the generic path, and so are pages for other kinds of ingredient. This class and {@link IntegratedTerminalsCompat} are the
 * only code that touches Integrated Terminals types (constitution VII); they are never loaded when the mod is not installed.
 */
final class IntegratedTerminalsBridge implements NetworkStorageBridge {
    @Override
    public String modId() {
        return ThirdPartyTable.INTEGRATED_TERMINALS;
    }

    @Override
    public boolean handles(AbstractContainerMenu menu) {
        return itemStorageTab(menu) != null;
    }

    @Override
    public ItemStack insert(ServerPlayer player, AbstractContainerMenu menu, ItemStack stack) {
        TerminalStorageTabIngredientComponentServer<?, ?> tab = itemStorageTab(menu);
        if (tab == null) {
            return stack;
        }
        return insertInto(tab, ((ContainerTerminalStorageBase<?>) menu).getSelectedChannel(), stack);
    }

    /** The item storage tab the terminal shows now, or null when the menu is not a terminal or the page is not one for items in the network. */
    private static TerminalStorageTabIngredientComponentServer<?, ?> itemStorageTab(AbstractContainerMenu menu) {
        if (!(menu instanceof ContainerTerminalStorageBase<?> container)) {
            return null;
        }
        ITerminalStorageTabServer tab = container.getTabServer(container.getSelectedTab());
        if (!(tab instanceof TerminalStorageTabIngredientComponentServer<?, ?> storage)
                || storage instanceof TerminalStorageTabIngredientComponentItemStackCraftingServer) {
            return null;
        }
        return storage.getIngredientNetwork().getComponent() == IngredientComponent.ITEMSTACK ? storage : null;
    }

    /** 060: the same page and the same conditions as the store path. */
    @Override
    public boolean handlesTake(AbstractContainerMenu menu) {
        return itemStorageTab(menu) != null;
    }

    /**
     * 060: takes up to the asked count (a full stack at most) of the item out of the channel the terminal has selected, the way its own client would ask for it, and
     * answers with what the network really gave (the network may hold less, or nothing).
     */
    @Override
    @SuppressWarnings("unchecked")
    public ItemStack take(ServerPlayer player, AbstractContainerMenu menu, ItemStack what, long handle) {
        TerminalStorageTabIngredientComponentServer<?, ?> tab = itemStorageTab(menu);
        if (tab == null) {
            return ItemStack.EMPTY;
        }
        INetworkIngredientsChannel<ItemStack, Integer> network = ((TerminalStorageTabIngredientComponentServer<ItemStack, Integer>) tab)
                .getIngredientNetwork()
                .getChannel(((ContainerTerminalStorageBase<?>) menu).getSelectedChannel());
        ItemStack prototype = what.copyWithCount(Math.max(1, Math.min(what.getCount(), what.getMaxStackSize())));
        // The match is on the item and its data only. The "exact" condition also matches the amount (the network answers nothing when it
        // holds fewer than the prototype's count), so an entry of 27 would never give its 27: the amount is only how much we ask for.
        return network.extract(prototype, IngredientComponent.ITEMSTACK.getMatcher().getExactMatchNoQuantityCondition(), false);
    }

    /** 060: what the backpack could not hold goes back the way it came in; the answer is what the network did not take back. */
    @Override
    public ItemStack giveBack(ServerPlayer player, AbstractContainerMenu menu, ItemStack stack, long handle) {
        return insert(player, menu, stack);
    }

    @SuppressWarnings("unchecked")
    private static ItemStack insertInto(TerminalStorageTabIngredientComponentServer<?, ?> tab, int channel, ItemStack stack) {
        INetworkIngredientsChannel<ItemStack, Integer> network =
                ((TerminalStorageTabIngredientComponentServer<ItemStack, Integer>) tab).getIngredientNetwork().getChannel(channel);
        return network.insert(stack, false);
    }
}
