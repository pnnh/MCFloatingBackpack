package pnnh.mcfloatingbackpack.inventory.pickup;

import pnnh.mcfloatingbackpack.inventory.tags.TagRules;

/**
 * 024: what the server accepts as a request to change the pickup blacklist: a known operation and a tag that follows the
 * 018 tag rules. Whether the backpack really has an auto pickup, and whether the list has room, is checked against the
 * backpack's real contents. Pure logic.
 */
public final class PickupBlacklistRequest {
    public enum Op {
        ADD,
        REMOVE;

        public byte toByte() {
            return (byte) ordinal();
        }

        /** @return null for anything that is not a known operation */
        public static Op fromByte(byte value) {
            Op[] all = values();
            return value >= 0 && value < all.length ? all[value] : null;
        }
    }

    private PickupBlacklistRequest() {
    }

    public static boolean validate(Op op, String tag) {
        return op != null && TagRules.isValid(tag);
    }
}
