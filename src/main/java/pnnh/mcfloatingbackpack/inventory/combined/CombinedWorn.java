package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.List;

/**
 * 065: which of the combined backpacks a player wears counts. The tier decides what is connected, so the highest tier wins; of equal tiers
 * the one that comes first in the search order (the list is in that order). Pure.
 */
public final class CombinedWorn {
    private CombinedWorn() {
    }

    /** The index of the combined backpack that counts, or {@code -1} if there is none. */
    public static int pick(List<Integer> tiersInSearchOrder) {
        int best = -1;
        int bestTier = Integer.MIN_VALUE;
        for (int i = 0; i < tiersInSearchOrder.size(); i++) {
            if (tiersInSearchOrder.get(i) > bestTier) {
                best = i;
                bestTier = tiersInSearchOrder.get(i);
            }
        }
        return best;
    }
}
