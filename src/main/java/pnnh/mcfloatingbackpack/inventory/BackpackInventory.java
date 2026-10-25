package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Mutable working copy of backpack contents (36 slots + panel cursor).
 * Load from / save to a floating-backpack ItemStack data component.
 */
public final class BackpackInventory {
    public static final int SIZE = 36;

    private final ItemStackHandler items = new ItemStackHandler(SIZE);
    private ItemStack cursor = ItemStack.EMPTY;

    public ItemStackHandler items() {
        return items;
    }

    public ItemStack getCursor() {
        return cursor;
    }

    public void setCursor(ItemStack cursor) {
        this.cursor = cursor.isEmpty() ? ItemStack.EMPTY : cursor;
    }

    public ItemStack getStackInSlot(int slot) {
        return items.getStackInSlot(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        items.setStackInSlot(slot, stack);
    }

    public static BackpackInventory fromContents(BackpackContents contents) {
        BackpackInventory inventory = new BackpackInventory();
        if (contents == null) {
            return inventory;
        }
        List<ItemStack> list = contents.items();
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = i < list.size() ? list.get(i) : ItemStack.EMPTY;
            inventory.setStackInSlot(
                    i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        inventory.setCursor(contents.cursor());
        return inventory;
    }

    public BackpackContents toContents() {
        List<ItemStack> list = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            list.add(getStackInSlot(i).copy());
        }
        return BackpackContents.normalize(list, getCursor().copy());
    }

    public static BackpackInventory fromItem(ItemStack backpack) {
        if (backpack.isEmpty()) {
            return new BackpackInventory();
        }
        BackpackContents contents =
                backpack.getOrDefault(ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty());
        return fromContents(contents);
    }

    public void saveToItem(ItemStack backpack) {
        if (backpack.isEmpty()) {
            return;
        }
        backpack.set(ModDataComponents.BACKPACK_CONTENTS.get(), toContents());
    }
}
