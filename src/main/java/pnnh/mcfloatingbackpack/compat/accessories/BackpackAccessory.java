package pnnh.mcfloatingbackpack.compat.accessories;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.slot.SlotReference;

import net.minecraft.world.item.ItemStack;

/**
 * 036: what Accessories is told about a backpack. Only one thing differs from its default for an item that fits a slot: a backpack
 * is <em>not</em> equipped by a right click. Accessories does that for every item that fits one of its slots, on the same right click
 * that opens the panel, and it swaps with whatever is worn there: the backpack in the hand and the one on the back change places, so
 * the panel opened on one, the hotbar showed the other, and every click that followed worked on a backpack the player was not
 * looking at. (Curios does not do this by default, so nothing is needed there.) A backpack still goes into the back slot the other
 * ways: dragged in the Accessories screen, or with the sneak use that equips it to the chest.
 */
final class BackpackAccessory implements Accessory {
    @Override
    public boolean canEquipFromUse(ItemStack stack, SlotReference reference) {
        return false;
    }
}
