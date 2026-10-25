package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Mutable working copy of one backpack item. Capacity follows that item's tier.
 */
public final class BackpackInventory {
    public static final int MAX_SIZE = 108;

    private final ItemStackHandler items;
    private ItemStack cursor = ItemStack.EMPTY;

    public BackpackInventory(int capacity) {
        int size = Math.max(1, Math.min(MAX_SIZE, capacity));
        this.items = new ItemStackHandler(size);
    }

    public int size() {
        return items.getSlots();
    }

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
        if (slot < 0 || slot >= size()) {
            return ItemStack.EMPTY;
        }
        return items.getStackInSlot(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= size()) {
            return;
        }
        items.setStackInSlot(slot, stack);
    }

    public static BackpackInventory fromContents(BackpackContents contents, int capacity) {
        BackpackInventory inventory = new BackpackInventory(capacity);
        if (contents == null) {
            return inventory;
        }
        BackpackContents fitted = contents.fit(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStackInSlot(i, fitted.items().get(i).copy());
        }
        inventory.setCursor(fitted.cursor());
        return inventory;
    }

    public BackpackContents toContents() {
        List<ItemStack> list = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            list.add(getStackInSlot(i).copy());
        }
        return BackpackContents.fit(list, getCursor().copy(), size());
    }

    public static int capacityOf(ItemStack backpack) {
        if (backpack.getItem() instanceof FloatingBackpackItem item) {
            return item.tier().capacity();
        }
        return BackpackTier.LEATHER.capacity();
    }

    public static BackpackInventory fromItem(ItemStack backpack) {
        int capacity = capacityOf(backpack);
        if (backpack.isEmpty()) {
            return new BackpackInventory(capacity);
        }
        BackpackContents contents = backpack.getOrDefault(
                ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty(capacity));
        return fromContents(contents, capacity);
    }

    public void saveToItem(ItemStack backpack) {
        if (backpack.isEmpty()) {
            return;
        }
        backpack.set(ModDataComponents.BACKPACK_CONTENTS.get(), toContents());
    }
}
