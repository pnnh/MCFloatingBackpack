package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 057: a warning at most once an hour. */
class ThrottleTest {
    @Test
    void theFirstCallPassesAndTheRestOfThePeriodDoesNot() {
        Throttle throttle = new Throttle(Throttle.HOUR_MS);
        assertTrue(throttle.tryAcquire(0));
        assertFalse(throttle.tryAcquire(1));
        assertFalse(throttle.tryAcquire(Throttle.HOUR_MS - 1));
        assertTrue(throttle.tryAcquire(Throttle.HOUR_MS));
        assertFalse(throttle.tryAcquire(Throttle.HOUR_MS + 1));
    }

    @Test
    void aClockThatStartsAtAnyNumberWorks() {
        Throttle throttle = new Throttle(10);
        assertTrue(throttle.tryAcquire(-1_000_000));
        assertFalse(throttle.tryAcquire(-999_995));
        assertTrue(throttle.tryAcquire(-999_990));
    }
}
