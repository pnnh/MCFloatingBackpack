package pnnh.mcfloatingbackpack.inventory;

import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

import net.minecraft.world.item.ItemStack;

/**
 * Left-click pick / place / merge / swap between a slot list and a panel cursor.
 * Kept free of player/world dependencies for unit testing with bootstrapped items.
 */
public final class BackpackClickLogic {
    private BackpackClickLogic() {
    }

    public static ItemStack leftClick(
            IntFunction<ItemStack> getSlot,
            ObjIntConsumer<ItemStack> setSlot,
            ItemStack cursor,
            int slotIndex) {
        ItemStack slotStack = getSlot.apply(slotIndex).copy();
        ItemStack held = cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy();

        if (held.isEmpty()) {
            if (!slotStack.isEmpty()) {
                setSlot.accept(ItemStack.EMPTY, slotIndex);
                return slotStack;
            }
            return ItemStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            setSlot.accept(held, slotIndex);
            return ItemStack.EMPTY;
        }

        if (ItemStack.isSameItemSameComponents(held, slotStack)) {
            int max = slotStack.getMaxStackSize();
            int space = max - slotStack.getCount();
            if (space <= 0) {
                return held;
            }
            int move = Math.min(space, held.getCount());
            slotStack.grow(move);
            held.shrink(move);
            setSlot.accept(slotStack, slotIndex);
            return held.isEmpty() ? ItemStack.EMPTY : held;
        }

        setSlot.accept(held, slotIndex);
        return slotStack;
    }

    public static void applyTo(BackpackInventory inventory, int slotIndex) {
        ItemStack newCursor = leftClick(
                inventory::getStackInSlot,
                (stack, index) -> inventory.setStackInSlot(index, stack),
                inventory.getCursor(),
                slotIndex);
        inventory.setCursor(newCursor);
    }
}
