package pnnh.mcfloatingbackpack.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.compat.ServerCompatLevels;
import pnnh.mcfloatingbackpack.inventory.worn.SlotLimit;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 061: what Curios is told about a floating backpack: it may go into a slot type only if no other floating backpack (of any tier) is in a
 * real slot of that type ({@link SlotLimit}). Curios asks when a stack is put into a slot ({@code DynamicStackHandler.isItemValid}); it
 * does not ask again later, so a second backpack that got in another way (a command, creative mode) is left where it is. The limit only
 * holds while this mod's compatibility with Curios is at FULL (the side of the entity decides which setting counts, as for the worn source).
 *
 * <p>Nothing else differs from Curios' default for an item: {@code canEquipFromUse} is not overridden, which makes it false, so a right
 * click does not equip a backpack (as before). Whatever goes wrong reads as "allowed": the limit must never be the reason a backpack
 * cannot be put anywhere.
 */
final class BackpackCurioItem implements ICurioItem {
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        try {
            LivingEntity entity = context.entity();
            if (entity == null || context.cosmetic()) {
                return true;
            }
            boolean enforced = entity.level().isClientSide()
                    ? CompatConfig.policy().specificAdaptation("curios")
                    : ServerCompatLevels.specificAdaptation("curios");
            if (!enforced) {
                return true;
            }
            ICurioStacksHandler handler = CuriosApi.getCuriosInventory(entity)
                    .flatMap(inventory -> inventory.getStacksHandler(context.identifier()))
                    .orElse(null);
            if (handler == null) {
                return true;
            }
            IDynamicStackHandler stacks = handler.getStacks();
            int others = 0;
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                if (slot != context.index() && FloatingBackpackItem.isBackpack(stacks.getStackInSlot(slot))) {
                    others++;
                }
            }
            return SlotLimit.allows(others, true);
        } catch (RuntimeException e) {
            return true;
        }
    }
}
