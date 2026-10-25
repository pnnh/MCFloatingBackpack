package pnnh.mcfloatingbackpack.client.panel;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;

/**
 * Client mirror of the server backpack. Updated only by network payloads.
 */
public final class ClientBackpackView {
    private static final ClientBackpackView INSTANCE = new ClientBackpackView();

    private final NonNullList<ItemStack> slots =
            NonNullList.withSize(BackpackInventory.SIZE, ItemStack.EMPTY);
    private ItemStack cursor = ItemStack.EMPTY;
    private BackpackRef activeRef;

    private ClientBackpackView() {
    }

    public static ClientBackpackView get() {
        return INSTANCE;
    }

    public ItemStack getStackInSlot(int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        return slots.get(index);
    }

    public ItemStack getCursor() {
        return cursor;
    }

    public BackpackRef getActiveRef() {
        return activeRef;
    }

    public void setActiveRef(BackpackRef activeRef) {
        this.activeRef = activeRef;
    }

    public void clearActiveRef() {
        this.activeRef = null;
    }

    public void applyFull(List<ItemStack> synced, ItemStack syncedCursor) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = i < synced.size() ? synced.get(i) : ItemStack.EMPTY;
            slots.set(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        cursor = syncedCursor == null || syncedCursor.isEmpty() ? ItemStack.EMPTY : syncedCursor.copy();
    }

    public void applySlotUpdate(int slotIndex, ItemStack stack, ItemStack syncedCursor) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            slots.set(slotIndex, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        cursor = syncedCursor == null || syncedCursor.isEmpty() ? ItemStack.EMPTY : syncedCursor.copy();
    }

    public void clear() {
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, ItemStack.EMPTY);
        }
        cursor = ItemStack.EMPTY;
        activeRef = null;
    }
}
