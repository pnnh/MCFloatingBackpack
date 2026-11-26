package pnnh.mcfloatingbackpack.inventory.quick;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;

/**
 * 067: the inventory side of {@link HandSwapPlace}. Empty cells are tried in {@link BackpackInventory#mainTargetOrder}: fitting
 * category rows, then the tier's own cells, then universal rows. The clicked cell is preferred when it accepts the hand stack.
 */
public final class HandSwapSlots {
    private HandSwapSlots() {
    }

    public static int destination(BackpackInventory inventory, int fromSlot, ItemStack hand) {
        if (inventory == null || fromSlot < 0 || fromSlot >= inventory.size()) {
            return -1;
        }
        boolean handEmpty = hand == null || hand.isEmpty();
        boolean fromAccepts = !handEmpty && inventory.canPlaceMain(fromSlot, hand);
        List<Integer> emptyAccepting = List.of();
        if (!handEmpty && !fromAccepts) {
            emptyAccepting = new ArrayList<>();
            for (int[] segment : inventory.mainTargetOrder(hand)) {
                for (int slot : segment) {
                    if (slot < 0 || slot >= inventory.size() || slot == fromSlot) {
                        continue;
                    }
                    if (inventory.getStackInSlot(slot).isEmpty() && inventory.canPlaceMain(slot, hand)) {
                        emptyAccepting.add(slot);
                    }
                }
            }
        }
        return HandSwapPlace.destination(fromSlot, handEmpty, fromAccepts, emptyAccepting);
    }
}
