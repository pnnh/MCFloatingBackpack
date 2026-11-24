package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.combined.PanelTargetRules.Decision;

/** 065: a panel request goes to the session's target only while that target is still exactly what was chosen. */
class PanelTargetRulesTest {
    private static final UUID ID = UUID.randomUUID();

    private static Decision decide(boolean session, boolean enabled, int worn, boolean isBackpack, UUID target, UUID chosen, int tier, boolean newer) {
        return PanelTargetRules.decide(session, enabled, worn, isBackpack, target, chosen, tier, newer);
    }

    @Test
    void aValidTargetIsUsed() {
        assertEquals(Decision.TARGET, decide(true, true, 2, true, ID, ID, 1, false));
        assertEquals(Decision.TARGET, decide(true, true, 2, true, ID, ID, 2, false));
    }

    @Test
    void withoutASessionTheBKeysBackpackIsUsed() {
        assertEquals(Decision.FALL_BACK, decide(false, true, 2, true, ID, ID, 1, false));
    }

    @Test
    void aSwitchedOffFeatureOrNoCombinedBackpackFallsBack() {
        assertEquals(Decision.FALL_BACK, decide(true, false, 2, true, ID, ID, 1, false));
        assertEquals(Decision.FALL_BACK, decide(true, true, -1, true, ID, ID, 1, false));
    }

    @Test
    void aTargetThatIsNoLongerABackpackFallsBack() {
        assertEquals(Decision.FALL_BACK, decide(true, true, 2, false, null, ID, 0, false));
    }

    @Test
    void anotherBackpackAtTheSamePlaceFallsBack() {
        assertEquals(Decision.FALL_BACK, decide(true, true, 2, true, UUID.randomUUID(), ID, 1, false));
        assertEquals(Decision.FALL_BACK, decide(true, true, 2, true, null, ID, 1, false));
    }

    @Test
    void aTierAboveTheCombinedBackpackFallsBack() {
        assertEquals(Decision.FALL_BACK, decide(true, true, 1, true, ID, ID, 2, false));
    }

    @Test
    void aBackpackSavedByANewerVersionFallsBack() {
        assertEquals(Decision.FALL_BACK, decide(true, true, 2, true, ID, ID, 1, true));
    }

    @Test
    void aSessionWithoutAnIdentityNeverMatches() {
        assertEquals(Decision.FALL_BACK, decide(true, true, 2, true, null, null, 1, false));
    }
}
