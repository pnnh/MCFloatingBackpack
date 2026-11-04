package pnnh.mcfloatingbackpack.inventory;

import java.util.HashSet;
import java.util.Set;

/**
 * Rules for a client's quick-transfer request, kept free of Minecraft types so they can be
 * unit-tested. The server runs {@link #validate} before touching any item.
 */
public final class QuickTransferRequest {
    /** Size of the player's main inventory plus hotbar (indices 0-35). */
    public static final int INVENTORY_SLOTS = 36;
    /** Largest backpack (netherite). */
    public static final int MAX_BACKPACK_SLOTS = 108;

    private QuickTransferRequest() {
    }

    public enum Direction {
        TO_BACKPACK,
        TO_INVENTORY;

        public byte toByte() {
            return (byte) ordinal();
        }

        /** @return null for anything that is not a known direction */
        public static Direction fromByte(byte value) {
            Direction[] all = values();
            return value >= 0 && value < all.length ? all[value] : null;
        }
    }

    /** Where the backpack being operated on lives (mirrors {@code BackpackRef.Source}). */
    public enum Holder {
        MAIN_HAND,
        OFF_HAND,
        CHEST,
        INVENTORY
    }

    /**
     * No duplicates, no out-of-range index, and a bounded count: inventory side 1..36 entries in
     * [0,35]; backpack side 1..backpackSize entries in [0,backpackSize-1].
     */
    public static boolean validate(Direction direction, int[] slots, int backpackSize) {
        if (direction == null || slots == null || slots.length == 0) {
            return false;
        }
        int bound = direction == Direction.TO_BACKPACK
                ? INVENTORY_SLOTS
                : Math.min(Math.max(backpackSize, 0), MAX_BACKPACK_SLOTS);
        if (slots.length > bound) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= bound || !seen.add(slot)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The player-inventory index whose stack is the backpack being used, which must never be
     * moved into its own storage; -1 when the backpack is not in indices 0-35 (offhand, chest).
     */
    public static int protectedInventoryIndex(Holder holder, int invIndex, int selectedSlot) {
        return switch (holder) {
            case MAIN_HAND -> selectedSlot;
            case INVENTORY -> invIndex;
            case OFF_HAND, CHEST -> -1;
        };
    }
}
