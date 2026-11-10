package pnnh.mcfloatingbackpack.inventory.tags;

import java.util.List;

/**
 * 018: the built-in tags. Stored as these stable English ids and shown through language keys, so changing the game
 * language never changes the data (a later "trash" feature recognises the {@code trash} id). 035: {@code crop},
 * {@code potion} and {@code disc} were added after the first ten; together with {@code food tool weapon armor ore wood redstone}
 * these are the ids of the category extensions, so a manual tag with one of them makes an item fit that category's row.
 */
public final class PresetTags {
    private static final List<String> IDS =
            List.of(
                    "stone", "wood", "food", "ore", "trash", "tool", "weapon", "armor", "plant", "redstone", "crop", "potion",
                    "disc");

    private PresetTags() {
    }

    public static List<String> ids() {
        return IDS;
    }

    public static String langKey(String id) {
        return "tag.mcfloatingbackpack.preset." + id;
    }

    public static boolean isPreset(String tag) {
        return tag != null && IDS.contains(tag);
    }
}
