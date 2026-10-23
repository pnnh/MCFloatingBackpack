package pnnh.mcfloatingbackpack.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Server-authoritative 27-slot backpack plus panel cursor stack.
 */
public final class BackpackInventory implements INBTSerializable<CompoundTag> {
    public static final int SIZE = 27;

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

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items.serializeNBT(provider));
        if (!cursor.isEmpty()) {
            tag.put("Cursor", cursor.save(provider));
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("Items")) {
            items.deserializeNBT(provider, tag.getCompound("Items"));
        }
        if (tag.contains("Cursor")) {
            cursor = ItemStack.parse(provider, tag.getCompound("Cursor")).orElse(ItemStack.EMPTY);
        } else {
            cursor = ItemStack.EMPTY;
        }
    }
}
