package pnnh.mcfloatingbackpack.inventory.trash;

/**
 * 022: the one tag the protection list guards. Items on the list may not be marked as trash; every other tag is theirs
 * to have. Pure logic: the caller counts how many of the selected item categories are protected.
 */
public final class TagProtection {
    /** The preset tag (018) that makes a dump pick an item up. */
    public static final String TRASH = "trash";

    private TagProtection() {
    }

    public static boolean blocks(String tag, int protectedCategories) {
        return TRASH.equals(tag) && protectedCategories > 0;
    }
}
