package pnnh.mcfloatingbackpack.inventory.quick;

import java.util.List;

import pnnh.mcfloatingbackpack.item.BackpackTier;

/**
 * 068: a quick-access item enters the bottom area only when it is the backpack's own tier, and that area holds at most one
 * quick-access item of any tier. Other areas are not limited. Pure logic: no Minecraft types.
 */
public final class QuickAccessPlace {
    public enum Verdict { OK, WRONG_TIER, DUPLICATE }

    private QuickAccessPlace() {
    }

    /**
     * @param bottom whether the stack would go into the bottom function area
     * @param backpack the backpack's tier, or {@code null} when its capacity is not a tier
     * @param incoming the tier of the stack being put in, or {@code null} when it is not a quick-access item
     * @param slotIndex the slot it goes into (the stack there leaves)
     * @param stored one entry per slot already in the area; {@code null} where the slot is not a quick-access item
     */
    public static Verdict check(boolean bottom, BackpackTier backpack, BackpackTier incoming, int slotIndex, List<BackpackTier> stored) {
        if (!bottom || incoming == null) {
            return Verdict.OK;
        }
        if (incoming != backpack) {
            return Verdict.WRONG_TIER;
        }
        int before = 0;
        for (BackpackTier tier : stored) {
            if (tier != null) {
                before++;
            }
        }
        boolean replacesOne = slotIndex >= 0 && slotIndex < stored.size() && stored.get(slotIndex) != null;
        int after = before - (replacesOne ? 1 : 0) + 1;
        return after > 1 && after > before ? Verdict.DUPLICATE : Verdict.OK;
    }
}
