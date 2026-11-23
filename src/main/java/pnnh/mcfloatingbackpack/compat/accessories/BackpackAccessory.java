package pnnh.mcfloatingbackpack.compat.accessories;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.slot.SlotReference;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.compat.ServerCompatLevels;
import pnnh.mcfloatingbackpack.inventory.worn.SlotLimit;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 036: what Accessories is told about a backpack. Only one thing differs from its default for an item that fits a slot: a backpack
 * is <em>not</em> equipped by a right click. Accessories does that for every item that fits one of its slots, on the same right click
 * that opens the panel, and it swaps with whatever is worn there: the backpack in the hand and the one on the back change places, so
 * the panel opened on one, the hotbar showed the other, and every click that followed worked on a backpack the player was not
 * looking at. (Curios does not do this by default, so nothing is needed there.) A backpack still goes into the back slot the other
 * ways: dragged in the Accessories screen, or with the sneak use that equips it to the chest.
 *
 * <p>061: and a backpack goes into a slot type only if no other floating backpack (of any tier) is in a real slot of that type ({@link
 * SlotLimit}). Accessories asks when a stack is put in; it does not ask again later, so a second one that got in another way (a command,
 * creative mode) is left where it is. The limit holds only while this mod's compatibility with Accessories is at FULL (the side of the
 * entity decides which setting counts). Whatever goes wrong reads as "allowed".
 */
final class BackpackAccessory implements Accessory {
    @Override
    public boolean canEquipFromUse(ItemStack stack, SlotReference reference) {
        return false;
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference reference) {
        try {
            if (reference.entity() == null) {
                return true;
            }
            boolean enforced = reference.entity().level().isClientSide()
                    ? CompatConfig.policy().specificAdaptation("accessories")
                    : ServerCompatLevels.specificAdaptation("accessories");
            if (!enforced) {
                return true;
            }
            Container accessories = reference.slotContainer().getAccessories();
            int others = 0;
            for (int slot = 0; slot < accessories.getContainerSize(); slot++) {
                if (slot != reference.slot() && FloatingBackpackItem.isBackpack(accessories.getItem(slot))) {
                    others++;
                }
            }
            return SlotLimit.allows(others, true);
        } catch (RuntimeException e) {
            return true;
        }
    }
}
