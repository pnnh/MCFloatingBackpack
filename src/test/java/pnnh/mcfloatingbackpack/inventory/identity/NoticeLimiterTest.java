package pnnh.mcfloatingbackpack.inventory.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.identity.NoticeLimiter.Reason;

/** 026: the same message to the same player at most once in a window. */
class NoticeLimiterTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void theWindowIs30Seconds() {
        assertEquals(30000L, NoticeLimiter.WINDOW_MS);
    }

    @Test
    void onlyTheFirstInAWindowGetsThrough() {
        NoticeLimiter limiter = new NoticeLimiter();
        assertTrue(limiter.shouldNotify(A, Reason.TOMBSTONE, 1000L));
        assertFalse(limiter.shouldNotify(A, Reason.TOMBSTONE, 1001L));
        assertFalse(limiter.shouldNotify(A, Reason.TOMBSTONE, 1000L + NoticeLimiter.WINDOW_MS - 1));
    }

    @Test
    void afterTheWindowItGetsThroughAgain() {
        NoticeLimiter limiter = new NoticeLimiter();
        assertTrue(limiter.shouldNotify(A, Reason.TOMBSTONE, 1000L));
        assertTrue(limiter.shouldNotify(A, Reason.TOMBSTONE, 1000L + NoticeLimiter.WINDOW_MS));
        assertFalse(limiter.shouldNotify(A, Reason.TOMBSTONE, 1000L + NoticeLimiter.WINDOW_MS + 1));
    }

    @Test
    void playersDoNotAffectEachOther() {
        NoticeLimiter limiter = new NoticeLimiter();
        assertTrue(limiter.shouldNotify(A, Reason.TOMBSTONE, 1000L));
        assertTrue(limiter.shouldNotify(B, Reason.TOMBSTONE, 1001L));
        assertFalse(limiter.shouldNotify(A, Reason.TOMBSTONE, 1002L));
    }
}
