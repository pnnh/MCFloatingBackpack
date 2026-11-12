package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.List;

/**
 * 035: the categories of the category extensions (ten, and since 051 nineteen) and the numbers around them. A category id is at once the category
 * of a row, the manual tag (018) that makes an item fit that category, and the last segment of the data tag
 * {@code mcfloatingbackpack:extension/<id>}. The order is fixed once released: it decides the order in which a
 * reconcile treats the categories. Pure logic with no Minecraft types.
 */
public final class ExtensionCategories {
    /** The ids, in their fixed order. */
    public static final List<String> IDS =
            List.of("food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc",
                    // 051: nine more, appended (the order is fixed once released)
                    "building", "natural", "plant", "decor", "mobdrop", "storage", "transport", "enchant", "meat");

    /**
     * 038: the category of the universal extensions: a row that takes any item. It is deliberately not in {@link #IDS}: the ones
     * there each have a data tag, an {@code <id>_extension} item and a preset tag name, and the code that walks {@code IDS} relies
     * on that. {@link #ALL} is those and then this one, in the fixed order (the last place is part of what is released).
     */
    public static final String GENERAL = "general";

    /** 038: every category a row can have, in the fixed order: those of {@link #IDS}, then {@link #GENERAL}. */
    public static final List<String> ALL = java.util.stream.Stream.concat(IDS.stream(), java.util.stream.Stream.of(GENERAL)).toList();

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
        return id != null && ALL.contains(id);
    }

    /** Position of the id in {@link #ALL}; unknown ids come last. */
    public static int orderOf(String id) {
        int index = id == null ? -1 : ALL.indexOf(id);
        return index < 0 ? ALL.size() : index;
    }
}
