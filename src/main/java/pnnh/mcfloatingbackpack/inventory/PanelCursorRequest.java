package pnnh.mcfloatingbackpack.inventory;

import java.util.HashSet;
import java.util.Set;

/**
 * Rules for a client's panel cursor-gesture request (013): drag-distribute, drag-gather,
 * double-click gather. Free of Minecraft types so it can be unit-tested; the server runs
 * {@link #validate} before touching any item.
 */
public final class PanelCursorRequest {
    private PanelCursorRequest() {
    }

    public enum Mode {
        DISTRIBUTE_LEFT,
        DISTRIBUTE_RIGHT,
        GATHER_DRAG,
        GATHER_ALL;

        public byte toByte() {
            return (byte) ordinal();
        }

        /** @return null for anything that is not a known mode */
        public static Mode fromByte(byte value) {
            Mode[] all = values();
            return value >= 0 && value < all.length ? all[value] : null;
        }
    }

    /**
     * Known mode, no duplicates, every index in [0, backpackSize), and never more entries than the
     * backpack has slots or {@link QuickTransferRequest#MAX_BACKPACK_SLOTS}; GATHER_ALL carries exactly one slot.
     */
    public static boolean validate(Mode mode, int[] slots, int backpackSize) {
        if (mode == null || slots == null || slots.length == 0) {
            return false;
        }
        int size = Math.min(Math.max(backpackSize, 0), QuickTransferRequest.MAX_BACKPACK_SLOTS);
        if (slots.length > size || (mode == Mode.GATHER_ALL && slots.length != 1)) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= size || !seen.add(slot)) {
                return false;
            }
        }
        return true;
    }
}
