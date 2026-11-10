package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.List;

/**
 * 035: the ten categories of the category extensions and the numbers around them. A category id is at once the category
 * of a row, the manual tag (018) that makes an item fit that category, and the last segment of the data tag
 * {@code mcfloatingbackpack:extension/<id>}. The order is fixed once released: it decides the order in which a
 * reconcile treats the categories. Pure logic with no Minecraft types.
 */
public final class ExtensionCategories {
    /** The ids, in their fixed order. */
    public static final List<String> IDS =
            List.of("food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc");

    /** One row is nine cells, whatever the backpack's tier. */
    public static final int ROW_CELLS = 9;
    /** Most rows (visible and hidden together) one backpack can hold; the same as the most tool cells of any tier. */
    public static final int MAX_ROWS = 35;
    /** Longest category id kept when foreign data is read (the same as a tag, see {@code TagRules.MAX_LENGTH}). */
    public static final int MAX_CATEGORY_LENGTH = 16;
    /** Most cells the tier capacity of a backpack can have. */
    public static final int MAX_TIER_CAPACITY = 108;
    /** Most cells the main area can have: the tier capacity and every row. */
    public static final int MAX_MAIN_SLOTS = MAX_TIER_CAPACITY + MAX_ROWS * ROW_CELLS;

    private ExtensionCategories() {
    }

    public static boolean isKnown(String id) {
        return id != null && IDS.contains(id);
    }

    /** Position of the id in {@link #IDS}; unknown ids come last. */
    public static int orderOf(String id) {
        int index = id == null ? -1 : IDS.indexOf(id);
        return index < 0 ? IDS.size() : index;
    }
}
