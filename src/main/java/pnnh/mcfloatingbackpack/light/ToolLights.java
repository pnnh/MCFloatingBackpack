package pnnh.mcfloatingbackpack.light;

import java.util.Map;

/**
 * 052: how bright the light is that follows a player who has a light item in the bottom tool area. One fixed table, never read from a
 * configuration, and the numbers are the ones the original game gives the same block (lantern 15, soul lantern 10, sea lantern 15,
 * glowstone 15, shroomlight 15, the three froglights 15, end rod 14, jack o'lantern 15), so the order between the lights is the one
 * the player knows: a soul lantern is dimmer than a lantern just as a soul torch is dimmer than a torch. (A torch is 14.) An item that
 * is in the tag but not in the table (added by a data pack or another mod) gets the emission of its own block; one that is not a block
 * item gets {@link #NON_BLOCK_LEVEL}. A light of 12 or more melts snow layers and ice within a few blocks of the player (snow above 11,
 * ice above 11 minus its own light block): that is the price of the brightness, and the soul lantern (10) is the one that does not.
 * Pure logic with no Minecraft types.
 */
public final class ToolLights {
    /** The brightest a light can be: the brightest light level of the game. */
    public static final int MAX_LEVEL = 15;
    /** The highest brightness that melts neither snow layers nor ice next to the light. */
    public static final int NO_MELT_LEVEL = 11;
    /** What an item that is in the tag, is not in the table and is not a block item gets. */
    public static final int NON_BLOCK_LEVEL = 8;

    private static final Map<String, Integer> TABLE = Map.ofEntries(
            Map.entry("minecraft:lantern", 15),
            Map.entry("minecraft:ochre_froglight", 15),
            Map.entry("minecraft:verdant_froglight", 15),
            Map.entry("minecraft:pearlescent_froglight", 15),
            Map.entry("minecraft:glowstone", 15),
            Map.entry("minecraft:sea_lantern", 15),
            Map.entry("minecraft:shroomlight", 15),
            Map.entry("minecraft:jack_o_lantern", 15),
            Map.entry("minecraft:end_rod", 14),
            Map.entry("minecraft:soul_lantern", 10));

    private ToolLights() {
    }

    /** The table: item id to light level. */
    public static Map<String, Integer> table() {
        return TABLE;
    }

    /**
     * @param itemId         the item's registry id
     * @param inTag          whether the item is in the tag {@code mcfloatingbackpack:tool_lights}
     * @param blockLuminance the emission of the item's block (0 if it is not a block item)
     * @param isBlockItem    whether the item places a block
     * @return 0 for an item that is not a light, otherwise 1 to {@link #MAX_LEVEL}
     */
    public static int level(String itemId, boolean inTag, int blockLuminance, boolean isBlockItem) {
        if (!inTag) {
            return 0;
        }
        Integer fixed = TABLE.get(itemId);
        if (fixed != null) {
            return fixed;
        }
        if (!isBlockItem) {
            return NON_BLOCK_LEVEL;
        }
        return Math.min(MAX_LEVEL, Math.max(1, blockLuminance));
    }
}
