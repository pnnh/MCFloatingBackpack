package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AllRequestRulesTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void inAllTheEpochMustBeTheSessions() {
        assertTrue(AllRequestRules.epochOk(true, 4, 4));
        assertFalse(AllRequestRules.epochOk(true, 4, 3));
        assertFalse(AllRequestRules.epochOk(true, 4, 5));
        assertFalse(AllRequestRules.epochOk(true, 4, 0));
    }

    @Test
    void outsideAllARequestForAllIsNeverTakenForOneBackpacksCell() {
        assertTrue(AllRequestRules.epochOk(false, 0, 0));
        assertFalse(AllRequestRules.epochOk(false, 0, 1));
        assertFalse(AllRequestRules.epochOk(false, 7, 7));
    }

    @Test
    void aMemberHoldsOnlyIfItIsStillTheSameBackpack() {
        assertTrue(AllRequestRules.memberOk(true, A, A, 2, 3, false));
        assertFalse(AllRequestRules.memberOk(false, A, A, 2, 3, false));
        assertFalse(AllRequestRules.memberOk(true, B, A, 2, 3, false));
        assertFalse(AllRequestRules.memberOk(true, null, A, 2, 3, false));
        assertFalse(AllRequestRules.memberOk(true, A, null, 2, 3, false));
        assertFalse(AllRequestRules.memberOk(true, A, A, 4, 3, false));
        assertFalse(AllRequestRules.memberOk(true, A, A, 2, -1, false));
        assertFalse(AllRequestRules.memberOk(true, A, A, 2, 3, true));
        assertTrue(AllRequestRules.memberOk(true, A, A, 3, 3, false));
    }
}
