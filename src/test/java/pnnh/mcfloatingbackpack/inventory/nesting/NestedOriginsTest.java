package pnnh.mcfloatingbackpack.inventory.nesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NestedOriginsTest {
    private final NestedOrigins<String, String, String> origins = new NestedOrigins<>(String::equals);

    @Test
    void nothingIsRememberedAtFirst() {
        assertFalse(origins.matches("alice", "bag A", "backpack"));
    }

    @Test
    void whatWasTakenOutMayGoBackIntoTheSameBackpack() {
        origins.remember("alice", "bag A", "backpack");
        assertTrue(origins.matches("alice", "bag A", "backpack"));
        assertTrue(origins.matches("alice", "bag A", "backpack"), "asking does not use the record up");
    }

    @Test
    void notIntoAnotherBackpack() {
        origins.remember("alice", "bag A", "backpack");
        assertFalse(origins.matches("alice", "bag B", "backpack"));
    }

    @Test
    void notAnotherItem() {
        origins.remember("alice", "bag A", "backpack");
        assertFalse(origins.matches("alice", "bag A", "box with backpack"));
    }

    @Test
    void notForAnotherPlayer() {
        origins.remember("alice", "bag A", "backpack");
        assertFalse(origins.matches("bob", "bag A", "backpack"));
    }

    @Test
    void aNewerRecordReplacesTheOlderOne() {
        origins.remember("alice", "bag A", "backpack");
        origins.remember("alice", "bag A", "box with backpack");
        assertFalse(origins.matches("alice", "bag A", "backpack"));
        assertTrue(origins.matches("alice", "bag A", "box with backpack"));
        assertEquals(1, origins.size());
    }

    @Test
    void forgetAndClearEmptyIt() {
        origins.remember("alice", "bag A", "backpack");
        origins.remember("bob", "bag B", "backpack");
        origins.forget("alice");
        assertFalse(origins.matches("alice", "bag A", "backpack"));
        assertTrue(origins.matches("bob", "bag B", "backpack"));
        origins.clear();
        assertEquals(0, origins.size());
    }
}
