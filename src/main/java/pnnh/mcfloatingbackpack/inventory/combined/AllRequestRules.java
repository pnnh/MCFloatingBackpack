package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.Objects;
import java.util.UUID;

/**
 * 066: whether a request for the "all" tab may be carried out. Two questions, both asked before anything is touched: does the request belong
 * to the tab the session is on (the epoch), and is each backpack it names still the one the epoch was made with (identity, tier, protection).
 * Pure.
 */
public final class AllRequestRules {
    private AllRequestRules() {
    }

    /**
     * @param sessionInAll the session is on the "all" tab
     * @param sessionEpoch the epoch of the session's member table (meaningless when not in "all")
     * @param requestEpoch the epoch the request carries; 0 for a request made for a single backpack's tab
     */
    public static boolean epochOk(boolean sessionInAll, int sessionEpoch, int requestEpoch) {
        if (sessionInAll) {
            return requestEpoch > 0 && requestEpoch == sessionEpoch;
        }
        // not on the "all" tab: a request made for "all" must never be read as a cell number of one backpack
        return requestEpoch == 0;
    }

    /**
     * @param isBackpack the stack at the member's reference is a floating backpack
     * @param identity its identity (null if none)
     * @param expectedIdentity the identity the member had in the epoch
     * @param tier its tier ordinal
     * @param combinedTier the worn combined backpack's tier ordinal (-1 = none)
     * @param protectedStack saved by a newer version
     */
    public static boolean memberOk(
            boolean isBackpack, UUID identity, UUID expectedIdentity, int tier, int combinedTier, boolean protectedStack) {
        return isBackpack
                && !protectedStack
                && combinedTier >= 0
                && tier <= combinedTier
                && expectedIdentity != null
                && Objects.equals(identity, expectedIdentity);
    }
}
