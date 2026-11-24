package pnnh.mcfloatingbackpack.inventory.combined;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;

/** 066: a backpack's main area as a {@link SlotAccess} (what the cursor gestures work on). */
public final class BackpackSlots implements SlotAccess<ItemStack> {
    private final BackpackInventory backpack;

    public BackpackSlots(BackpackInventory backpack) {
        this.backpack = backpack;
    }

    @Override
    public int size() {
        return backpack.size();
    }

    @Override
    public ItemStack get(int index) {
        return backpack.getStackInSlot(index);
    }

    @Override
    public void set(int index, ItemStack stack) {
        backpack.setStackInSlot(index, stack);
    }

    @Override
    public boolean canPlace(int index, ItemStack stack) {
        return backpack.canPlaceMain(index, stack);
    }
}
