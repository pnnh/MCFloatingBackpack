package pnnh.mcfloatingbackpack.inventory.trash;

import java.util.HashSet;
import java.util.Set;

import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest;

/**
 * Rules for a client's "send to the trash can" request (022), free of Minecraft types so they can be unit-tested. The
 * server runs {@link #validate} before touching any item; whether the slots really hold something it may take is then
 * decided from the real contents.
 */
public final class TrashSendRequest {
    /** Menu slot numbers stay below this, the same bound as a quick-transfer request from a container. */
    public static final int MAX_MENU_SLOT = QuickTransferRequest.MAX_MENU_SLOTS;

    private TrashSendRequest() {
    }

    public enum Source {
        /** Dump the main-area stacks whose category is tagged as trash. No slots. */
        DUMP,
        /** Move the stacks in these main-area slots. */
        MAIN_SLOTS,
        /** Move the stack in one slot of the open vanilla inventory or chest menu. */
        MENU_SLOT;

        public byte toByte() {
            return (byte) ordinal();
        }

        /** @return null for anything that is not a known source */
        public static Source fromByte(byte value) {
            Source[] all = values();
            return value >= 0 && value < all.length ? all[value] : null;
        }
    }

    public static boolean validate(Source source, int[] slots, int containerId, int mainSize) {
        if (source == null || slots == null) {
            return false;
        }
        return switch (source) {
            case DUMP -> slots.length == 0;
            case MAIN_SLOTS -> distinctInside(slots, mainSize);
            case MENU_SLOT -> slots.length == 1 && slots[0] >= 0 && slots[0] < MAX_MENU_SLOT && containerId >= 0;
        };
    }

    private static boolean distinctInside(int[] slots, int mainSize) {
        int size = Math.min(Math.max(mainSize, 0), QuickTransferRequest.MAX_BACKPACK_SLOTS);
        if (slots.length == 0 || slots.length > size) {
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
