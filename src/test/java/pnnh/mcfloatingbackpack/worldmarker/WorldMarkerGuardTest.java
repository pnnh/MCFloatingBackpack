package pnnh.mcfloatingbackpack.worldmarker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.worldmarker.WorldMarkerGuard.Decision;

/** 041: the decision table of data-model.md, and the revision that is written. */
class WorldMarkerGuardTest {
    private static final int CURRENT = 1;
    private static final String VERSION = "0.1.0";

    private static MarkerRead marker(int revision, String version) {
        return new MarkerRead.Valid(new WorldMarker(revision, version));
    }

    private static Decision decide(MarkerRead read, boolean accept) {
        return WorldMarkerGuard.decide(read, CURRENT, accept, VERSION, WorldMarkerGuard.revisionToWrite(null, CURRENT, read));
    }

    @Test
    void noMarkerPassesAndWrites_whetherOrNotTheRiskIsAccepted() {
        assertEquals(Decision.PASS_AND_WRITE, decide(MarkerRead.MISSING, false));
        assertEquals(Decision.PASS_AND_WRITE, decide(MarkerRead.MISSING, true));
    }

    @Test
    void anUnusableMarkerPassesAndWrites_itNeverBlocksAStart() {
        assertEquals(Decision.PASS_AND_WRITE, decide(new MarkerRead.Corrupt("x"), false));
        assertEquals(Decision.PASS_AND_WRITE, decide(new MarkerRead.Corrupt("x"), true));
    }

    @Test
    void aLowerMarkerPassesAndWrites() {
        assertEquals(Decision.PASS_AND_WRITE, decide(marker(0, VERSION), false));
        assertEquals(Decision.PASS_AND_WRITE, decide(marker(0, null), true));
    }

    @Test
    void theSameRevisionAndVersionPassesWithoutWriting() {
        assertEquals(Decision.PASS, decide(marker(CURRENT, VERSION), false));
        assertEquals(Decision.PASS, decide(marker(CURRENT, VERSION), true));
    }

    @Test
    void theSameRevisionOfAnotherModVersionIsNeverBlocked_itOnlyUpdatesTheVersion() {
        assertEquals(Decision.PASS_AND_WRITE, decide(marker(CURRENT, "0.0.9"), false));
        assertEquals(Decision.PASS_AND_WRITE, decide(marker(CURRENT, null), false));
    }

    @Test
    void aVersionThatCannotBeToldDoesNotRewriteForTheVersion() {
        MarkerRead read = marker(CURRENT, "0.0.9");
        assertEquals(Decision.PASS, WorldMarkerGuard.decide(read, CURRENT, false, null, CURRENT));
    }

    @Test
    void aHigherMarkerIsRefused_exactlyOneRevisionAboveToo() {
        assertEquals(Decision.REFUSE, decide(marker(CURRENT + 1, VERSION), false));
        assertEquals(Decision.REFUSE, decide(marker(99, "9.9.9"), false));
    }

    @Test
    void aHigherMarkerIsAcceptedWhenThePlayerAcceptsTheRisk() {
        assertEquals(Decision.ACCEPTED, decide(marker(CURRENT + 1, VERSION), true));
        assertEquals(Decision.ACCEPTED, decide(marker(99, null), true));
    }

    @Test
    void theModVersionNumberIsNeverWhatIsCompared_onlyTheRevision() {
        // An older mod version number on a marker of the same revision is no downgrade; a newer one on a lower revision neither.
        assertEquals(Decision.PASS_AND_WRITE, decide(marker(CURRENT, "9.9.9"), false));
        assertEquals(Decision.PASS_AND_WRITE, decide(marker(0, "9.9.9"), false));
    }

    @Test
    void aWriteIsNeverAskedForWhenRefusedOrAccepted() {
        for (int stored : new int[] {CURRENT + 1, 50}) {
            for (boolean accept : new boolean[] {false, true}) {
                Decision decision = WorldMarkerGuard.decide(marker(stored, VERSION), CURRENT, accept, VERSION, 1_000);
                assertEquals(accept ? Decision.ACCEPTED : Decision.REFUSE, decision);
            }
        }
    }

    @Test
    void theRevisionWrittenIsTheCurrentOne() {
        assertEquals(CURRENT, WorldMarkerGuard.revisionToWrite(null, CURRENT, MarkerRead.MISSING));
        assertEquals(CURRENT, WorldMarkerGuard.revisionToWrite(null, CURRENT, marker(0, null)));
        assertEquals(CURRENT, WorldMarkerGuard.revisionToWrite(null, CURRENT, new MarkerRead.Corrupt("x")));
    }

    @Test
    void aMarkerOnlyGoesUp() {
        assertEquals(5, WorldMarkerGuard.revisionToWrite(null, CURRENT, marker(5, null)));
        assertEquals(5, WorldMarkerGuard.revisionToWrite(2, CURRENT, marker(5, null)));
    }

    @Test
    void theDevelopmentOverrideIsWhatIsWritten_butNotBelowTheMarker() {
        assertEquals(99, WorldMarkerGuard.revisionToWrite(99, CURRENT, MarkerRead.MISSING));
        assertEquals(99, WorldMarkerGuard.revisionToWrite(99, CURRENT, marker(CURRENT, VERSION)));
        assertEquals(99, WorldMarkerGuard.revisionToWrite(99, CURRENT, marker(99, VERSION)));
        assertEquals(CURRENT, WorldMarkerGuard.revisionToWrite(-3, CURRENT, MarkerRead.MISSING));
    }

    @Test
    void aDevelopmentOverrideRaisesAMarkerThatIsAlreadyCurrent() {
        MarkerRead read = marker(CURRENT, VERSION);
        int target = WorldMarkerGuard.revisionToWrite(99, CURRENT, read);
        assertEquals(Decision.PASS_AND_WRITE, WorldMarkerGuard.decide(read, CURRENT, false, VERSION, target));
    }
}
