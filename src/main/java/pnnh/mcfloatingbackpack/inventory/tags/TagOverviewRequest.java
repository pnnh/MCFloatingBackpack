package pnnh.mcfloatingbackpack.inventory.tags;

/**
 * 025: the part of a tag-overview request that can be checked without looking at the backpack. The request names an item
 * category by its registry id (the item may not be in the backpack at all, so there is no slot index as in 018's
 * {@link TagRequest}); the server then checks it against the backpack's real tags. Pure logic.
 */
public final class TagOverviewRequest {
    /** Longest item id string a request may carry. */
    public static final int MAX_ITEM_ID_LENGTH = 256;

    public enum Op {
        ADD,
        REMOVE,
        /** Removes every tag of the category. */
        CLEAR;

        public byte toByte() {
            return (byte) ordinal();
        }

        /** The operation for a wire byte, or null if the byte is not one. */
        public static Op fromByte(byte value) {
            Op[] all = values();
            return value >= 0 && value < all.length ? all[value] : null;
        }
    }

    private TagOverviewRequest() {
    }

    /** ADD and REMOVE need a valid tag (018's rules); CLEAR takes none, which is sent as the empty string. */
    public static boolean validate(Op op, String itemId, String tag) {
        if (op == null || itemId == null || itemId.isEmpty() || itemId.length() > MAX_ITEM_ID_LENGTH) {
            return false;
        }
        if (op == Op.CLEAR) {
            return tag != null && tag.isEmpty();
        }
        return TagRules.isValid(tag);
    }
}
