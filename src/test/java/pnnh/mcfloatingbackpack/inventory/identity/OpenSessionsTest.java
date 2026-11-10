package pnnh.mcfloatingbackpack.inventory.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.identity.OpenSessions.Verdict;

class OpenSessionsTest {
    @Test
    void withoutAnOpenPanelThereIsNothingToCompare() {
        OpenSessions<String, String> sessions = new OpenSessions<>();
        assertEquals(Verdict.NO_SESSION, sessions.check("p", "a"));
        assertFalse(sessions.has("p"));
    }

    @Test
    void theSameBackpackIsFineAndAnotherOneIsStale() {
        OpenSessions<String, String> sessions = new OpenSessions<>();
        sessions.opened("p", "a");
        assertEquals(Verdict.SAME, sessions.check("p", "a"));
        assertEquals(Verdict.STALE, sessions.check("p", "b"));
        assertEquals(Verdict.STALE, sessions.check("p", null), "no backpack there any more");
    }

    @Test
    void playersAreIndependent() {
        OpenSessions<String, String> sessions = new OpenSessions<>();
        sessions.opened("p", "a");
        sessions.opened("q", "b");
        assertEquals(Verdict.SAME, sessions.check("q", "b"));
        assertEquals(Verdict.STALE, sessions.check("p", "b"));
    }

    @Test
    void reopeningOnAnotherBackpackMovesTheSession() {
        OpenSessions<String, String> sessions = new OpenSessions<>();
        sessions.opened("p", "a");
        sessions.opened("p", "b");
        assertEquals(Verdict.SAME, sessions.check("p", "b"));
        assertEquals(Verdict.STALE, sessions.check("p", "a"));
    }

    @Test
    void closingForgetsAndABackpackWithoutIdentityIsNotCompared() {
        OpenSessions<String, String> sessions = new OpenSessions<>();
        sessions.opened("p", "a");
        sessions.closed("p");
        assertEquals(Verdict.NO_SESSION, sessions.check("p", "b"));
        sessions.opened("p", "a");
        sessions.opened("p", null);
        assertTrue(!sessions.has("p"));
        assertEquals(Verdict.NO_SESSION, sessions.check("p", "b"));
    }
}
