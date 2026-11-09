package pnnh.mcfloatingbackpack.compat.curios;

import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.worn.WornSource;

/**
 * 033: the back slot of Curios (the official 9.x) as a place a floating backpack can be worn, after the chest slot. Only the real
 * stacks count (not the cosmetic ones); a slot Curios has switched off counts as empty; and the player's "do not draw this slot"
 * switch is reported as {@link #hiddenRender}, which makes the back layer draw nothing while the backpack is still the one worn.
 *
 * <p>Whatever goes wrong (no Curios data on the entity, the slot is missing, a slot number out of range) reads as an empty stack and
 * never throws. Writing a stack back needs no extra call: Curios compares every slot with its last known stack each tick, and syncs
 * and announces a change by itself.
 *
 * <p>This class, and {@link CuriosCompat}, are the only code that touches Curios types (constitution VII); it is never loaded when
 * Curios is not.
 */
final class CuriosWornSource implements WornSource<LivingEntity, ItemStack> {
    /** The Curios slot type this source reads. */
    private static final String SLOT = "back";

    @Override
    public int id() {
        return BackpackRef.Source.CURIOS.ordinal();
    }

    @Override
    public int order() {
        // after the chest (0); the Accessories back slot will be 20
        return 10;
    }

    @Override
    public boolean active() {
        // asked at every search, so a changed setting takes effect at once; on a dedicated server the (client) config is not loaded and this is FULL
        return CompatConfig.policy().specificAdaptation("curios");
    }

    private static Optional<ICurioStacksHandler> handler(LivingEntity entity) {
        try {
            return CuriosApi.getCuriosInventory(entity).flatMap(inventory -> inventory.getStacksHandler(SLOT));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public int slotCount(LivingEntity entity) {
        return handler(entity).map(ICurioStacksHandler::getSlots).orElse(0);
    }

    @Override
    public ItemStack get(LivingEntity entity, int slot) {
        try {
            ICurioStacksHandler handler = handler(entity).orElse(null);
            if (handler == null) {
                return ItemStack.EMPTY;
            }
            IDynamicStackHandler stacks = handler.getStacks();
            if (slot < 0 || slot >= stacks.getSlots()) {
                return ItemStack.EMPTY;
            }
            NonNullList<Boolean> active = handler.getActiveStates();
            if (active.size() > slot && !active.get(slot)) {
                return ItemStack.EMPTY;
            }
            return stacks.getStackInSlot(slot);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void set(LivingEntity entity, int slot, ItemStack stack) {
        try {
            ICurioStacksHandler handler = handler(entity).orElse(null);
            if (handler == null) {
                return;
            }
            IDynamicStackHandler stacks = handler.getStacks();
            if (slot >= 0 && slot < stacks.getSlots()) {
                stacks.setStackInSlot(slot, stack);
            }
        } catch (RuntimeException e) {
            // nothing to write into
        }
    }

    @Override
    public boolean hiddenRender(LivingEntity entity, int slot) {
        // Curios' own back layer draws a slot only when renders.size() > slot && renders.get(slot): the same test, turned round
        try {
            ICurioStacksHandler handler = handler(entity).orElse(null);
            if (handler == null) {
                return false;
            }
            NonNullList<Boolean> renders = handler.getRenders();
            return !(renders.size() > slot && renders.get(slot));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
