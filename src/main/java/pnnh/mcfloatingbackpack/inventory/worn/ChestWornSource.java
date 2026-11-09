package pnnh.mcfloatingbackpack.inventory.worn;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;

/** 032: the chest slot, where a backpack has always been worn: one slot, always there, never hidden. The first source of the search. */
final class ChestWornSource implements WornSource<LivingEntity, ItemStack> {
    @Override
    public int id() {
        return BackpackRef.Source.CHEST.ordinal();
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public boolean active() {
        return true;
    }

    @Override
    public int slotCount(LivingEntity entity) {
        return 1;
    }

    @Override
    public ItemStack get(LivingEntity entity, int slot) {
        return slot == 0 ? entity.getItemBySlot(EquipmentSlot.CHEST) : ItemStack.EMPTY;
    }

    @Override
    public void set(LivingEntity entity, int slot, ItemStack stack) {
        if (slot == 0) {
            entity.setItemSlot(EquipmentSlot.CHEST, stack);
        }
    }
}
