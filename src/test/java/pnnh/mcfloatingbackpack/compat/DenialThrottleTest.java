package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class DenialThrottleTest {
    private static final UUID P1 = UUID.randomUUID();
    private static final UUID P2 = UUID.randomUUID();

    @Test
    void theFirstRefusalIsToldAndTheSameScreenAgainIsNot() {
        DenialThrottle throttle = new DenialThrottle();
        assertTrue(throttle.shouldNotify(P1, 5, "a"));
        assertFalse(throttle.shouldNotify(P1, 5, "a"));
        assertFalse(throttle.shouldNotify(P1, 5, "a"));
    }

    @Test
    void anotherScreenOrAnotherModIsToldAgain() {
        DenialThrottle throttle = new DenialThrottle();
        assertTrue(throttle.shouldNotify(P1, 5, "a"));
        assertTrue(throttle.shouldNotify(P1, 6, "a"));
        assertFalse(throttle.shouldNotify(P1, 6, "a"));
        assertTrue(throttle.shouldNotify(P1, 6, "b"));
    }

    @Test
    void forgettingMakesTheNextRefusalToldEvenForTheSameNumber() {
        DenialThrottle throttle = new DenialThrottle();
        assertTrue(throttle.shouldNotify(P1, 5, "a"));
        throttle.forget(P1);
        assertTrue(throttle.shouldNotify(P1, 5, "a"));
        throttle.forget(UUID.randomUUID());
        assertFalse(throttle.shouldNotify(P1, 5, "a"));
    }

    @Test
    void playersDoNotAffectEachOther() {
        DenialThrottle throttle = new DenialThrottle();
        assertTrue(throttle.shouldNotify(P1, 5, "a"));
        assertTrue(throttle.shouldNotify(P2, 5, "a"));
        throttle.forget(P1);
        assertFalse(throttle.shouldNotify(P2, 5, "a"));
    }

    @Test
    void severalThreadsAskingAboutTheSameScreenGetOneYes() {
        DenialThrottle throttle = new DenialThrottle();
        AtomicInteger yes = new AtomicInteger();
        IntStream.range(0, 200).parallel().forEach(i -> {
            if (throttle.shouldNotify(P1, 7, "a")) {
                yes.incrementAndGet();
            }
        });
        assertEquals(1, yes.get());
    }
}
