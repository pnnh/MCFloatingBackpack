package pnnh.mcfloatingbackpack.inventory.tags;

import java.util.HashSet;
import java.util.Set;

/**
 * 018: the part of a tag request that can be checked without looking at the backpack. The server then resolves the
 * slots to item ids with the real contents, so the client is never trusted to say what an item is.
 */
public final class TagRequest {
    /** The most slots one request may name (the biggest main area). */
    public static final int MAX_SLOTS = 108;

    public enum Op {
        ADD,
        REMOVE;

        public byte toByte() {
            return (byte) ordinal();
        }

        /** The operation for a wire byte, or null if the byte is not one. */
        public static Op fromByte(byte value) {
            Op[] all = values();
            return value >= 0 && value < all.length ? all[value] : null;
        }
    }

    private TagRequest() {
    }

    public static boolean validate(Op op, int[] slots, String tag, int mainSize) {
        if (op == null || slots == null || slots.length == 0 || slots.length > Math.min(mainSize, MAX_SLOTS)) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= mainSize || !seen.add(slot)) {
                return false;
            }
        }
        return TagRules.isValid(tag);
    }
}
