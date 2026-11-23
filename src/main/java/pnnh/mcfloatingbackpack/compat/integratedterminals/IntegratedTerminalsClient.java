package pnnh.mcfloatingbackpack.compat.integratedterminals;

import java.util.Optional;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabClient;
import org.cyclops.integratedterminals.client.gui.container.ContainerScreenTerminalStorage;
import org.cyclops.integratedterminals.core.terminalstorage.TerminalStorageTabIngredientComponentClient;
import org.cyclops.integratedterminals.core.terminalstorage.TerminalStorageTabIngredientComponentItemStackCraftingClient;
import org.cyclops.integratedterminals.core.terminalstorage.slot.TerminalStorageSlotIngredient;

import pnnh.mcfloatingbackpack.client.compat.NetworkEntry;
import pnnh.mcfloatingbackpack.client.compat.NetworkHeld;
import pnnh.mcfloatingbackpack.client.compat.NetworkTerminalClient;

/**
 * 060: recognises, on the client, the entry of an Integrated Terminals terminal's item storage page that is under the cursor. The entries are
 * not slots of the menu: the screen draws them and works out which one the cursor is over itself ({@code getStorageSlotIndexAtPosition}, which
 * counts the scrolled-off rows too), and the page ({@code getSlot(channel, index)}) knows the entry's item. The page must be the one that
 * holds items from the network and not the crafting page (the crafting page's class extends the storage page's, as on the server). The amount
 * is not asked: the server asks the network for a full stack. Public methods of the mod only; with {@link IntegratedTerminalsBridge} and
 * {@link IntegratedTerminalsCompat} the only code that touches its types.
 */
@OnlyIn(Dist.CLIENT)
final class IntegratedTerminalsClient implements NetworkTerminalClient {
    @Override
    public Optional<NetworkEntry> entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (!(screen instanceof ContainerScreenTerminalStorage<?, ?> terminal)) {
            return Optional.empty();
        }
        TerminalStorageTabIngredientComponentClient<?, ?> storage = itemPage(terminal);
        if (storage == null) {
            return Optional.empty();
        }
        int index = terminal.getStorageSlotIndexAtPosition(mouseX, mouseY);
        if (index < 0) {
            return Optional.empty();
        }
        Optional<? extends TerminalStorageSlotIngredient<?, ?>> cell = storage.getSlot(terminal.getMenu().getSelectedChannel(), index);
        if (cell.isEmpty() || !(cell.get().getInstance() instanceof ItemStack stack) || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NetworkEntry(stack.copyWithCount(1), 0L));
    }

    /**
     * An entry the player picked up with a plain click: Integrated Terminals keeps it as the page's "active entry" (its index in the list and
     * an amount) and draws it at the mouse itself; the menu's carried stack stays empty until the entry is clicked into a slot. Only the page's
     * own public getters are used.
     */
    @Override
    public Optional<NetworkHeld> held(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof ContainerScreenTerminalStorage<?, ?> terminal)) {
            return Optional.empty();
        }
        TerminalStorageTabIngredientComponentClient<?, ?> storage = itemPage(terminal);
        if (storage == null || storage.getActiveSlotId() < 0 || storage.getActiveSlotQuantity() <= 0) {
            return Optional.empty();
        }
        Optional<? extends TerminalStorageSlotIngredient<?, ?>> cell = storage.getSlot(terminal.getMenu().getSelectedChannel(), storage.getActiveSlotId());
        if (cell.isEmpty() || !(cell.get().getInstance() instanceof ItemStack stack) || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new NetworkHeld(stack.copyWithCount(1), storage.getActiveSlotQuantity()));
    }

    @Override
    public void release(AbstractContainerScreen<?> screen) {
        if (screen instanceof ContainerScreenTerminalStorage<?, ?> terminal) {
            TerminalStorageTabIngredientComponentClient<?, ?> storage = itemPage(terminal);
            if (storage != null) {
                storage.resetActiveSlot();
            }
        }
    }

    /** The selected page when it is the one that holds items from the network (not the crafting page, whose class extends it), else null. */
    private static TerminalStorageTabIngredientComponentClient<?, ?> itemPage(ContainerScreenTerminalStorage<?, ?> terminal) {
        Optional<ITerminalStorageTabClient<?>> tab = terminal.getSelectedClientTab();
        if (tab.isEmpty()
                || !(tab.get() instanceof TerminalStorageTabIngredientComponentClient<?, ?> storage)
                || storage instanceof TerminalStorageTabIngredientComponentItemStackCraftingClient
                || storage.getIngredientComponent() != IngredientComponent.ITEMSTACK) {
            return null;
        }
        return storage;
    }
}
