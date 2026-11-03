package pnnh.mcfloatingbackpack.inventory;

import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

import net.minecraft.world.item.ItemStack;

/**
 * Left/right-click pick / place / merge / swap between a slot list and a panel cursor, mirroring
 * vanilla {@code AbstractContainerMenu} pickup rules (left = whole stack, right = half pickup /
 * single-item place). Kept free of player/world dependencies for unit testing with bootstrapped
 * items.
 */
public final class BackpackClickLogic {
    private BackpackClickLogic() {
    }

    public static ItemStack leftClick(
            IntFunction<ItemStack> getSlot,
            ObjIntConsumer<ItemStack> setSlot,
            ItemStack cursor,
            int slotIndex) {
        return click(getSlot, setSlot, cursor, slotIndex, false);
    }

    public static ItemStack rightClick(
            IntFunction<ItemStack> getSlot,
            ObjIntConsumer<ItemStack> setSlot,
            ItemStack cursor,
            int slotIndex) {
        return click(getSlot, setSlot, cursor, slotIndex, true);
    }

    public static ItemStack click(
            IntFunction<ItemStack> getSlot,
            ObjIntConsumer<ItemStack> setSlot,
            ItemStack cursor,
            int slotIndex,
            boolean rightClick) {
        ItemStack slotStack = getSlot.apply(slotIndex).copy();
        ItemStack held = cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy();

        if (held.isEmpty()) {
            if (slotStack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!rightClick) {
                setSlot.accept(ItemStack.EMPTY, slotIndex);
                return slotStack;
            }
            // Right click with an empty cursor picks up the ceiling half, leaving the floor half.
            int pickupCount = (slotStack.getCount() + 1) / 2;
            ItemStack picked = slotStack.copy();
            picked.setCount(pickupCount);
            slotStack.shrink(pickupCount);
            setSlot.accept(slotStack.isEmpty() ? ItemStack.EMPTY : slotStack, slotIndex);
            return picked;
        }

        if (slotStack.isEmpty()) {
            if (!rightClick) {
                setSlot.accept(held, slotIndex);
                return ItemStack.EMPTY;
            }
            // Right click with a held stack places a single item into the empty slot.
            ItemStack placed = held.copy();
            placed.setCount(1);
            setSlot.accept(placed, slotIndex);
            held.shrink(1);
            return held.isEmpty() ? ItemStack.EMPTY : held;
        }

        if (ItemStack.isSameItemSameComponents(held, slotStack)) {
            int max = slotStack.getMaxStackSize();
            int space = max - slotStack.getCount();
            if (space <= 0) {
                return held;
            }
            int move = rightClick ? Math.min(1, held.getCount()) : Math.min(space, held.getCount());
            move = Math.min(move, space);
            slotStack.grow(move);
            held.shrink(move);
            setSlot.accept(slotStack, slotIndex);
            return held.isEmpty() ? ItemStack.EMPTY : held;
        }

        if (held.getCount() > slotStack.getMaxStackSize()) {
            return held;
        }
        setSlot.accept(held, slotIndex);
        return slotStack;
    }

    public static void applyTo(BackpackInventory inventory, int slotIndex, boolean rightClick) {
        ItemStack newCursor = click(
                inventory::getStackInSlot,
                (stack, index) -> inventory.setStackInSlot(index, stack),
                inventory.getCursor(),
                slotIndex,
                rightClick);
        inventory.setCursor(newCursor);
    }
}
