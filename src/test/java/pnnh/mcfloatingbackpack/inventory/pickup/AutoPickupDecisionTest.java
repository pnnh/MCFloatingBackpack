package pnnh.mcfloatingbackpack.inventory.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.pickup.AutoPickupDecision.Outcome;

/** 024: the decision table of the auto pickup (tool active x blacklist). */
class AutoPickupDecisionTest {
    @Test
    void noActiveToolMeansVanillaWhateverTheBlacklistSays() {
        assertEquals(Outcome.VANILLA, AutoPickupDecision.decide(false, false));
        assertEquals(Outcome.VANILLA, AutoPickupDecision.decide(false, true));
    }

    @Test
    void blacklistedItemIsBlocked() {
        assertEquals(Outcome.BLOCK, AutoPickupDecision.decide(true, true));
    }

    @Test
    void otherwiseTheBackpackTakesIt() {
        assertEquals(Outcome.TAKE, AutoPickupDecision.decide(true, false));
    }
}
