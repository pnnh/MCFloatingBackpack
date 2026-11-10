package pnnh.mcfloatingbackpack.client.panel;

import java.util.Map;

/**
 * 037: how each category of the extension rows looks in the panel: one colour for the line and the corner mark, a translucent
 * version of it over the slot, and the id of the extension item whose picture shows faintly in an empty cell. Drawn in code, no
 * textures. The colours are a first draft to be looked at in the game; they live in this one table. Plain integers and strings, no
 * Minecraft types, so the table can be tested.
 */
public final class CategoryStyle {
    /** Over the slot's own colour (0xFF8B8B8B), about 16 % opaque: only a hint of the colour (was 35 %, which looked too dark beside the ordinary grid). */
    private static final int FILL_ALPHA = 0x2A000000;
    private static final int NEUTRAL = 0xFF808080;

    private static final Map<String, Integer> EDGE = Map.of(
            "food", 0xFFF0A030,
            "tool", 0xFF4F8FE0,
            "weapon", 0xFFD04040,
            "armor", 0xFF9A6AD0,
            "ore", 0xFF2FC4C0,
            "crop", 0xFF6FBF3F,
            "wood", 0xFF9C6A3A,
            "potion", 0xFFE060B0,
            "redstone", 0xFF8B1A1A,
            "disc", 0xFF2F3A8F);

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
        return (edge(category) & 0x00FFFFFF) | FILL_ALPHA;
    }

    /** The id (in this mod's namespace) of the extension item that stands for the category; null for an unknown category. */
    public static String ghostItemId(String category) {
        return known(category) ? category + "_extension" : null;
    }
}
