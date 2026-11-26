package pnnh.mcfloatingbackpack.inventory.quick;

import java.util.List;

/**
 * 067: where the main-hand stack goes when a whole-stack swap takes {@code fromSlot}. The clicked slot is used when it accepts
 * the hand stack. Otherwise the first other empty slot that accepts it, in the order the caller supplies (the backpack's normal
 * insert order). Nowhere to put a non-empty hand means the swap must not happen. Pure logic.
 */
public final class HandSwapPlace {
    private HandSwapPlace() {
    }

    /**
     * @param fromSlot the cell whose stack moves to the hand
     * @param handEmpty the hand has nothing to put back
     * @param fromAccepts the clicked cell can hold the hand stack (ignored when the hand is empty)
     * @param emptyAccepting empty main-area cells that can hold the hand stack, in the order to try; the clicked cell may be listed
     * @return the cell that receives the hand stack, or {@code -1} when a non-empty hand has nowhere to go
     */
    public static int destination(int fromSlot, boolean handEmpty, boolean fromAccepts, List<Integer> emptyAccepting) {
        if (fromSlot < 0) {
            return -1;
        }
        if (handEmpty || fromAccepts) {
            return fromSlot;
        }
        if (emptyAccepting == null) {
            return -1;
        }
        for (int slot : emptyAccepting) {
            if (slot >= 0 && slot != fromSlot) {
                return slot;
            }
        }
        return -1;
    }
}
