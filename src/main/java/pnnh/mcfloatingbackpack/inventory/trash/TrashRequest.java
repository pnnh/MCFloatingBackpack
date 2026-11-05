package pnnh.mcfloatingbackpack.inventory.trash;

import java.util.HashSet;
import java.util.Set;

/**
 * Rules for a client's trash can request (021), free of Minecraft types so they can be unit-tested. The server runs
 * {@link #validate} before touching any item. The slots are trash slots (0..8), never item ids.
 */
public final class TrashRequest {
    public static final int GROUPS = TrashBin.MAX_GROUPS;

    private TrashRequest() {
    }

    public enum Action {
        /** The whole cursor stack goes into the trash can. No slots. */
        ADD,
        /** The group in one trash slot goes to the cursor. Exactly one slot. */
        TAKE,
        /** Groups move from the trash can into the backpack's main area. One to nine distinct slots. */
        RESTORE;

        public byte toByte() {
            return (byte) ordinal();
        }

        /** @return null for anything that is not a known action */
        public static Action fromByte(byte value) {
            Action[] all = values();
            return value >= 0 && value < all.length ? all[value] : null;
        }
    }

    public static boolean validate(Action action, int[] slots) {
        if (action == null || slots == null) {
            return false;
        }
        return switch (action) {
            case ADD -> slots.length == 0;
            case TAKE -> slots.length == 1 && inRange(slots[0]);
            case RESTORE -> distinctInRange(slots);
        };
    }

    private static boolean inRange(int slot) {
        return slot >= 0 && slot < GROUPS;
    }

    private static boolean distinctInRange(int[] slots) {
        if (slots.length == 0 || slots.length > GROUPS) {
            return false;
        }
        Set<Integer> seen = new HashSet<>();
        for (int slot : slots) {
            if (!inRange(slot) || !seen.add(slot)) {
                return false;
            }
        }
        return true;
    }
}
