package pnnh.mcfloatingbackpack.compat.accessories;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;

import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.worn.WornSource;

/**
 * 034: the back slot of Accessories (Wisp Forest) as a place a floating backpack can be worn, after the chest slot and the Curios back
 * slot. Only the real stacks count (not the cosmetic ones), and the player's "do not draw this slot" switch is reported as
 * {@link #hiddenRender}, which makes the back layer draw nothing while the backpack is still the one worn.
 *
 * <p>Whatever goes wrong (the entity has no Accessories data, it has no back slot, a slot number out of range) reads as an empty stack
 * and never throws. Writing a stack back needs no extra call: every tick Accessories compares each slot with its last known stack, and
 * syncs it and announces a change by itself.
 *
 * <p>This class and {@link AccessoriesCompat} are, with the client classes next to them, the only code that touches Accessories types
 * (constitution VII); none of it is loaded when Accessories is not.
 */
final class AccessoriesWornSource implements WornSource<LivingEntity, ItemStack> {
    /** The Accessories slot type this source reads. */
    private static final String SLOT = "back";

    @Override
    public int id() {
        return BackpackRef.Source.ACCESSORIES.ordinal();
    }

    @Override
    public int order() {
        // after the chest (0) and the Curios back slot (10)
        return 20;
    }

    @Override
    public boolean active() {
        // asked at every search, so a changed setting takes effect at once; on a dedicated server the (client) config is not loaded and this is FULL
        return CompatConfig.policy().specificAdaptation("accessories");
    }

    /** The entity's back slot, or {@code null} if it has none (no Accessories data, no such slot, anything unexpected). */
    private static AccessoriesContainer container(LivingEntity entity) {
        try {
            AccessoriesCapability capability = AccessoriesCapability.get(entity);
            return capability == null ? null : capability.getContainers().get(SLOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public int slotCount(LivingEntity entity) {
        try {
            AccessoriesContainer container = container(entity);
            return container == null ? 0 : container.getAccessories().getContainerSize();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Override
    public ItemStack get(LivingEntity entity, int slot) {
        try {
            AccessoriesContainer container = container(entity);
            if (container == null) {
                return ItemStack.EMPTY;
            }
            Container accessories = container.getAccessories();
            return slot < 0 || slot >= accessories.getContainerSize() ? ItemStack.EMPTY : accessories.getItem(slot);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void set(LivingEntity entity, int slot, ItemStack stack) {
        try {
            AccessoriesContainer container = container(entity);
            if (container == null) {
                return;
            }
            Container accessories = container.getAccessories();
            if (slot >= 0 && slot < accessories.getContainerSize()) {
                accessories.setItem(slot, stack);
            }
        } catch (RuntimeException e) {
            // nothing to write into
        }
    }

    @Override
    public boolean hiddenRender(LivingEntity entity, int slot) {
        try {
            AccessoriesContainer container = container(entity);
            return container != null && !container.shouldRender(slot);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
