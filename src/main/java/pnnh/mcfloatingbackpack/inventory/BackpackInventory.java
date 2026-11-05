package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.inventory.trash.TrashContents;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;
import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * Mutable working copy of one backpack item. Capacity follows that item's tier.
 */
public final class BackpackInventory {
    public static final int MAX_SIZE = 108;

    private final ItemStackHandler items;
    /** The three tool areas (016), indexed by {@link PanelArea#ordinal()}; the MAIN slot of the array is unused. */
    private final ItemStackHandler[] tools = new ItemStackHandler[PanelArea.values().length];
    private ItemStack cursor = ItemStack.EMPTY;
    private ItemTags tags = ItemTags.EMPTY;
    private TrashContents trash = TrashContents.EMPTY;

    public BackpackInventory(int capacity) {
        int size = Math.max(1, Math.min(MAX_SIZE, capacity));
        this.items = new ItemStackHandler(size);
        for (PanelArea area : PanelArea.values()) {
            if (area.isTool()) {
                tools[area.ordinal()] = new ItemStackHandler(ToolAreaCapacity.of(area, size));
            }
        }
    }

    public int size() {
        return items.getSlots();
    }

    public ItemStackHandler items() {
        return items;
    }

    /** The slots of a tool area; the main slots are {@link #items()}. */
    public ItemStackHandler handler(PanelArea area) {
        return area.isTool() ? tools[area.ordinal()] : items;
    }

    /** Slot count of an area; for a tool area this can exceed its capacity after a capacity decrease. */
    public int size(PanelArea area) {
        return handler(area).getSlots();
    }

    public ItemStack getStackInSlot(PanelArea area, int slot) {
        ItemStackHandler handler = handler(area);
        if (slot < 0 || slot >= handler.getSlots()) {
            return ItemStack.EMPTY;
        }
        return handler.getStackInSlot(slot);
    }

    public void setStackInSlot(PanelArea area, int slot, ItemStack stack) {
        ItemStackHandler handler = handler(area);
        if (slot < 0 || slot >= handler.getSlots()) {
            return;
        }
        handler.setStackInSlot(slot, stack);
    }

    /** The three tool areas as stored in the data component. */
    public ToolAreaContents toolContents() {
        return ToolAreaContents.normalize(
                list(PanelArea.LEFT), list(PanelArea.RIGHT), list(PanelArea.BOTTOM));
    }

    /** Copies of the stacks of a tool area, its whole stored list (for the placement rules). */
    public List<ItemStack> toolStacks(PanelArea area) {
        return list(area);
    }

    private List<ItemStack> list(PanelArea area) {
        ItemStackHandler handler = handler(area);
        List<ItemStack> out = new ArrayList<>(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); i++) {
            out.add(handler.getStackInSlot(i).copy());
        }
        return out;
    }

    /**
     * 021: whether the right area holds a trash can, which is what makes the trash can panel available. Looks at the
     * whole stored list, not at the cells the panel currently shows.
     */
    public boolean hasTrashCan() {
        for (int i = 0; i < size(PanelArea.RIGHT); i++) {
            if (getStackInSlot(PanelArea.RIGHT, i).is(ModItems.TRASH_CAN.get())) {
                return true;
            }
        }
        return false;
    }

    /** The tags of this backpack, by item category (018). */
    public ItemTags tags() {
        return tags;
    }

    public void setTags(ItemTags tags) {
        this.tags = tags == null ? ItemTags.EMPTY : tags;
    }

    /** What the trash can holds (021), newest group first. */
    public TrashContents trash() {
        return trash;
    }

    public void setTrash(TrashContents trash) {
        this.trash = trash == null ? TrashContents.EMPTY : trash;
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
        inventory.setTags(fitted.tags());
        inventory.setTrash(fitted.trash());
        for (PanelArea area : PanelArea.values()) {
            if (!area.isTool()) {
                continue;
            }
            List<ItemStack> stored = fitted.tools().list(area);
            ItemStackHandler handler = inventory.handler(area);
            handler.setSize(stored.size());
            for (int i = 0; i < stored.size(); i++) {
                handler.setStackInSlot(i, stored.get(i).copy());
            }
        }
        return inventory;
    }

    public BackpackContents toContents() {
        List<ItemStack> list = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            list.add(getStackInSlot(i).copy());
        }
        return BackpackContents.fit(list, getCursor().copy(), toolContents(), tags, trash, size());
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
