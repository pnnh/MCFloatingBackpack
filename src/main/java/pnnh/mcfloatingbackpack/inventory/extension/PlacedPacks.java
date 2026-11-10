package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 038: how many packs of each category a backpack counts as placed, which is what {@link RowReconciler} turns into rows. The
 * category extensions count wherever they are (a pack in any tool area, or on the panel's cursor, is one). The universal
 * extensions (one item per backpack tier) only count when they are
 *
 * <ul>
 *   <li>of the backpack's own tier (an iron one does nothing in a leather backpack),
 *   <li>in the left or the right area (not the bottom one),
 *   <li>and then once per area, however many there are in it, so that at most two count: one for the left, one for the right;
 *       one held on the panel's cursor counts too (it will land somewhere; 035: what is on the cursor counts as placed), but the
 *       total never goes past two.
 * </ul>
 *
 * Whatever does not count is an ordinary item: nothing is refused, nothing is removed. The answer does not depend on the order of
 * the packs. Pure logic with no Minecraft types.
 */
public final class PlacedPacks {
    public enum Place {
        LEFT,
        RIGHT,
        BOTTOM,
        CURSOR
    }

    /**
     * @param category the category of the pack
     * @param tier the tier a universal extension belongs to (any stable id, such as {@code "leather"}); null for a category extension
     * @param place where it is
     */
    public record Pack(String category, String tier, Place place) {
    }

    /** The most universal rows a backpack can have: one per side area. */
    public static final int MAX_UNIVERSAL = 2;

    private PlacedPacks() {
    }

    /** @param backpackTier the tier of the backpack, in the same terms as {@link Pack#tier()}; null when it is not a known tier */
    public static Map<String, Integer> count(List<Pack> packs, String backpackTier) {
        Map<String, Integer> placed = new HashMap<>();
        boolean left = false;
        boolean right = false;
        boolean cursor = false;
        for (Pack pack : packs) {
            if (pack.tier() == null) {
                placed.merge(pack.category(), 1, Integer::sum);
                continue;
            }
            if (backpackTier == null || !backpackTier.equals(pack.tier())) {
                continue;
            }
            switch (pack.place()) {
                case LEFT -> left = true;
                case RIGHT -> right = true;
                case CURSOR -> cursor = true;
                case BOTTOM -> {
                }
            }
        }
        int universal = Math.min(MAX_UNIVERSAL, (left ? 1 : 0) + (right ? 1 : 0) + (cursor ? 1 : 0));
        if (universal > 0) {
            placed.put(ExtensionCategories.GENERAL, universal);
        }
        return placed;
    }
}
