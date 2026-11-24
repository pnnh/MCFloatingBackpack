package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.Objects;
import java.util.UUID;

/**
 * 065: whether a panel request is for the combined session's target backpack or falls back to the backpack the B key opens. Asked for every
 * panel request, so it looks at the target alone and never works out the member list. Pure.
 */
public final class PanelTargetRules {
    public enum Decision {
        /** The target the session names. */
        TARGET,
        /** No session, or it no longer holds: what the B key would open (the existing stale-panel guard then sorts the rest out). */
        FALL_BACK
    }

    /**
     * @param hasSession the player has a combined session
     * @param enabled {@code [combined].enabled}
     * @param combinedWornTier the tier of the combined backpack worn, or -1 for none
     * @param targetIsBackpack the stack at the target reference is a floating backpack
     * @param targetIdentity its identity, or {@code null}
     * @param sessionIdentity the identity the session's target had when it was chosen
     * @param targetTier its tier
     * @param targetProtected it was saved by a newer version of the mod
     */
    public static Decision decide(
            boolean hasSession,
            boolean enabled,
            int combinedWornTier,
            boolean targetIsBackpack,
            UUID targetIdentity,
            UUID sessionIdentity,
            int targetTier,
            boolean targetProtected) {
        if (!hasSession || !enabled || combinedWornTier < 0 || !targetIsBackpack || targetProtected) {
            return Decision.FALL_BACK;
        }
        if (targetTier > combinedWornTier) {
            return Decision.FALL_BACK;
        }
        // a session's target always has an identity (it is given one when chosen); a target without one is not what was chosen
        if (sessionIdentity == null || !Objects.equals(targetIdentity, sessionIdentity)) {
            return Decision.FALL_BACK;
        }
        return Decision.TARGET;
    }

    private PanelTargetRules() {
    }
}
