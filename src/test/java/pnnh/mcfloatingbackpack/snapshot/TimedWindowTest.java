package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.TimedWindow.Due;

/** 057: the timed round hands out only the backpacks that changed, once its time has come. */
class TimedWindowTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void nothingIsDueBeforeTheRoundOrWhenNothingChanged() {
        TimedWindow<String> window = new TimedWindow<>(1000);
        assertEquals(List.of(), window.due(5000));
        window.mark(A, "a", 100);
        assertEquals(List.of(), window.due(1099));
        assertTrue(window.due(1100).size() == 1);
        assertEquals(List.of(), window.due(2200), "the next round came and nothing changed since: nothing is written");
    }

    @Test
    void aLaterChangeReplacesWhatWaitsAndEveryChangedBackpackIsHandedOutOnce() {
        TimedWindow<String> window = new TimedWindow<>(1000);
        window.mark(A, "a1", 0);
        window.mark(B, "b1", 10);
        window.mark(A, "a2", 20);
        List<Due<String>> due = window.due(1000);
        assertEquals(List.of(new Due<>(A, "a2"), new Due<>(B, "b1")), due);
        assertTrue(window.isEmpty());
    }

    @Test
    void theNextRoundStartsWhenTheOldOneIsHandedOut() {
        TimedWindow<String> window = new TimedWindow<>(1000);
        window.mark(A, "a", 0);
        assertEquals(1, window.due(1500).size());
        window.mark(A, "a2", 1600);
        assertEquals(List.of(), window.due(2499), "the next round is 1000 after the one at 1500");
        assertEquals(1, window.due(2500).size());
    }

    @Test
    void aTinyOrNegativeIntervalIsStillAtLeastOneMillisecond() {
        TimedWindow<String> window = new TimedWindow<>(-5);
        window.mark(A, "a", 10);
        assertEquals(1, window.due(11).size());
    }
}
