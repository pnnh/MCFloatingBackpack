package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;

/**
 * 035: whether an extension pack may be put into a tool area. A pack adds a row; a backpack holds at most
 * {@link ExtensionCategories#MAX_ROWS} rows (visible and hidden together), so a pack that would need one more row than that is
 * refused (a pack whose category has a hidden row waiting needs no new row). Any other item, and any pack when the rows
 * suffice, is accepted; the area itself plays no part, and packs may be placed several times over. A universal extension (038)
 * is always accepted.
 */
public final class ExtensionPackRules {
    private ExtensionPackRules() {
    }

    /**
     * @param incoming the stack about to be put into a tool slot
     * @param incomingCounted whether it is already counted among the placed packs, which it is when it comes from the panel's
     *     own cursor (the cursor counts as placed); a stack from the hand or the menu's carried stack is not
     */
    public static boolean canPlace(BackpackInventory inventory, ItemStack incoming, boolean incomingCounted) {
        String category = ExtensionPacks.categoryOf(incoming);
        if (category == null) {
            return true;
        }
        // 038: a universal extension is never refused, wherever it goes: it only counts in the left or right area of a backpack of
        // its own tier, and anywhere else it is an ordinary item. If it would make more rows than the most there can be, the
        // reconcile adds no row for it (it does not refuse it).
        if (ExtensionPacks.tierOf(incoming) != null) {
            return true;
        }
        Map<String, Integer> placedAfter = new HashMap<>(inventory.placedPacks());
        if (!incomingCounted) {
            placedAfter.merge(category, 1, Integer::sum);
        }
        return ExtensionRules.fits(inventory.rowSet(), placedAfter, ExtensionCategories.MAX_ROWS);
    }
}
