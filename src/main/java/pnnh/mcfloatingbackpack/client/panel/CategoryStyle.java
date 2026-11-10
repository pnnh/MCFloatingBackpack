package pnnh.mcfloatingbackpack.client.panel;

import java.util.Map;

import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;

/**
 * 037: how each category of the extension rows looks in the panel: one colour for the line and the corner mark, a translucent
 * version of it over the slot, and the id of the extension item whose picture shows faintly in an empty cell. Drawn in code, no
 * textures. The colours are a first draft to be looked at in the game; they live in this one table. Plain integers and strings, no
 * Minecraft types, so the table can be tested.
 */
public final class CategoryStyle {
    /** Over the slot's own colour (0xFF8B8B8B), about 16 % opaque: only a hint of the colour (was 35 %, which looked too dark beside the ordinary grid). */
    private static final int FILL_ALPHA = 0x2A000000;
    /** 038: a near white tint at 16 % cannot be seen on the slot's grey, so the universal row is laid over a good deal stronger (about 29 %). */
    private static final int FILL_ALPHA_GENERAL = 0x4A000000;
    private static final int NEUTRAL = 0xFF808080;

    private static final Map<String, Integer> EDGE = Map.ofEntries(
            Map.entry("food", 0xFFF0A030),
            Map.entry("tool", 0xFF4F8FE0),
            Map.entry("weapon", 0xFFD04040),
            Map.entry("armor", 0xFF9A6AD0),
            Map.entry("ore", 0xFF2FC4C0),
            Map.entry("crop", 0xFF6FBF3F),
            Map.entry("wood", 0xFF9C6A3A),
            Map.entry("potion", 0xFFE060B0),
            Map.entry("redstone", 0xFF8B1A1A),
            Map.entry("disc", 0xFF2F3A8F),
            // 038: the universal row, a near white: none of the ten is white, and white shows well over the slot's grey
            Map.entry("general", 0xFFF2F2F2));

    private CategoryStyle() {
    }

    public static boolean known(String category) {
        return category != null && EDGE.containsKey(category);
    }

    /** Colour of the separator lines and the corner mark (opaque). A category this version does not know is neutral grey. */
    public static int edge(String category) {
        return known(category) ? EDGE.get(category) : NEUTRAL;
    }

    /** Translucent colour laid over the slot's well. */
    public static int fill(String category) {
        return (edge(category) & 0x00FFFFFF) | (ExtensionCategories.GENERAL.equals(category) ? FILL_ALPHA_GENERAL : FILL_ALPHA);
    }

    /**
     * The id (in this mod's namespace) of the extension item that stands for the category, shown faintly in an empty cell of its row;
     * null for an unknown category and for the universal one, which has five items (one for each tier): the panel picks the one of
     * the backpack's own tier (038).
     */
    public static String ghostItemId(String category) {
        return known(category) && !ExtensionCategories.GENERAL.equals(category) ? category + "_extension" : null;
    }
}
