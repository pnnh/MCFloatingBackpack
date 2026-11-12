package pnnh.mcfloatingbackpack.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 049: the cooldown that keeps notices from flooding the chat and a held button from pouring everything at once. */
class CooldownTest {
    @Test
    void theFirstTryAlwaysGoes() {
        assertTrue(new Cooldown<String>().tryAcquire("a", 100, 20));
    }

    @Test
    void insideTheCooldownItDoesNotGo() {
        Cooldown<String> cooldown = new Cooldown<>();
        assertTrue(cooldown.tryAcquire("a", 100, 20));
        assertFalse(cooldown.tryAcquire("a", 101, 20));
        assertFalse(cooldown.tryAcquire("a", 119, 20));
    }

    @Test
    void afterTheCooldownItGoesAgain() {
        Cooldown<String> cooldown = new Cooldown<>();
        assertTrue(cooldown.tryAcquire("a", 100, 20));
        assertTrue(cooldown.tryAcquire("a", 120, 20));
        assertFalse(cooldown.tryAcquire("a", 121, 20));
    }

    @Test
    void aRefusedTryDoesNotPushTheEndAway() {
        Cooldown<String> cooldown = new Cooldown<>();
        assertTrue(cooldown.tryAcquire("a", 100, 20));
        for (long tick = 101; tick < 120; tick++) {
            assertFalse(cooldown.tryAcquire("a", tick, 20));
        }
        assertTrue(cooldown.tryAcquire("a", 120, 20), "twenty ticks after the one that went, however many tries came between");
    }

    @Test
    void keysDoNotInfluenceEachOther() {
        Cooldown<String> cooldown = new Cooldown<>();
        assertTrue(cooldown.tryAcquire("a", 100, 20));
        assertTrue(cooldown.tryAcquire("b", 100, 20));
        assertFalse(cooldown.tryAcquire("a", 105, 20));
    }

    @Test
    void aClockThatWentBackwardsDoesNotLockTheKeyOut() {
        Cooldown<String> cooldown = new Cooldown<>();
        assertTrue(cooldown.tryAcquire("a", 1000, 20));
        assertTrue(cooldown.tryAcquire("a", 5, 20), "a new world's clock starts again from a small number");
    }

    @Test
    void forgetRemovesTheMatchingKeys() {
        Cooldown<String> cooldown = new Cooldown<>();
        cooldown.tryAcquire("p1:full", 100, 20);
        cooldown.tryAcquire("p1:empty", 100, 20);
        cooldown.tryAcquire("p2:full", 100, 20);
        cooldown.forget(key -> key.startsWith("p1:"));
        assertEquals(1, cooldown.size());
        assertTrue(cooldown.tryAcquire("p1:full", 101, 20));
        assertFalse(cooldown.tryAcquire("p2:full", 101, 20));
    }
}
