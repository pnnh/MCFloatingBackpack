package pnnh.mcfloatingbackpack.client.panel;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.item.BackpackTier;

/**
 * Client mirror of the server backpack. Updated only by network payloads.
 */
public final class ClientBackpackView {
    private static final ClientBackpackView INSTANCE = new ClientBackpackView();

    private NonNullList<ItemStack> slots =
            NonNullList.withSize(BackpackTier.LEATHER.capacity(), ItemStack.EMPTY);
    private ItemStack cursor = ItemStack.EMPTY;
    private BackpackRef activeRef;

    private ClientBackpackView() {
    }

    public static ClientBackpackView get() {
        return INSTANCE;
    }

    public int slotCount() {
        return slots.size();
    }

    public int rows() {
        return Math.max(1, slots.size() / PanelSlots.COLS);
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
        int count = synced == null ? 0 : synced.size();
        if (count <= 0) {
            count = BackpackTier.LEATHER.capacity();
        }
        boolean sizeChanged = count != slots.size();
        NonNullList<ItemStack> next = NonNullList.withSize(count, ItemStack.EMPTY);
        for (int i = 0; i < count; i++) {
            ItemStack stack = synced != null && i < synced.size() ? synced.get(i) : ItemStack.EMPTY;
            next.set(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        slots = next;
        cursor = syncedCursor == null || syncedCursor.isEmpty() ? ItemStack.EMPTY : syncedCursor.copy();
        if (sizeChanged) {
            FloatingPanelState.get().setScrollRow(0);
        }
        FloatingPanelState.get().setTotalRows(rows());
    }

    public void applySlotUpdate(int slotIndex, ItemStack stack, ItemStack syncedCursor) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            slots.set(slotIndex, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        cursor = syncedCursor == null || syncedCursor.isEmpty() ? ItemStack.EMPTY : syncedCursor.copy();
    }

    public void clear() {
        slots = NonNullList.withSize(BackpackTier.LEATHER.capacity(), ItemStack.EMPTY);
        cursor = ItemStack.EMPTY;
        activeRef = null;
        FloatingPanelState.get().setScrollRow(0);
        FloatingPanelState.get().setTotalRows(BackpackTier.LEATHER.rows());
    }
}
