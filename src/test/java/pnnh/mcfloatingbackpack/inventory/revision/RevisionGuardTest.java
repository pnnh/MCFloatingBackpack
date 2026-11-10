package pnnh.mcfloatingbackpack.inventory.revision;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.revision.RevisionGuard.Decision;

class RevisionGuardTest {
    @Test
    void aBackpackThatIsNotNewerIsAllowedWhateverElse() {
        for (boolean open : new boolean[] {false, true}) {
            for (boolean window : new boolean[] {false, true}) {
                assertEquals(Decision.ALLOW, RevisionGuard.decide(0, 1, open, window));
                assertEquals(Decision.ALLOW, RevisionGuard.decide(1, 1, open, window));
                assertEquals(Decision.ALLOW, RevisionGuard.decide(-3, 1, open, window));
            }
        }
    }

    @Test
    void aNewerBackpackIsRefusedQuietlyOnAnyPathThatIsNotAnOpen() {
        assertEquals(Decision.REFUSE, RevisionGuard.decide(2, 1, false, false));
        // an open window does not let another path through: an auto pickup must never confirm the protection away
        assertEquals(Decision.REFUSE, RevisionGuard.decide(2, 1, false, true));
    }

    @Test
    void theFirstOpenOfANewerBackpackIsRefusedAndOpensTheWindow() {
        assertEquals(Decision.REFUSE_FIRST, RevisionGuard.decide(2, 1, true, false));
        assertEquals(Decision.REFUSE_FIRST, RevisionGuard.decide(99, 1, true, false));
    }

    @Test
    void anOpenInsideTheWindowConfirms() {
        assertEquals(Decision.CONFIRMED, RevisionGuard.decide(2, 1, true, true));
    }

    @Test
    void theBorderIsTheCurrentRevision() {
        assertEquals(Decision.ALLOW, RevisionGuard.decide(5, 5, true, false));
        assertEquals(Decision.REFUSE_FIRST, RevisionGuard.decide(6, 5, true, false));
    }
}
