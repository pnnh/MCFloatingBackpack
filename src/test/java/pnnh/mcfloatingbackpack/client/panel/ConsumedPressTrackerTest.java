package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsumedPressTrackerTest {
    @Test
    void releaseOfSwallowedPressIsSwallowedOnce() {
        ConsumedPressTracker t = new ConsumedPressTracker();
        t.press(1);
        assertTrue(t.release(1));
        assertFalse(t.release(1), "second release has no matching press");
    }

    @Test
    void releaseWithoutSwallowedPressPassesThrough() {
        // Press happened before the panel opened (e.g. the right-click that opened it): its
        // release must reach the game so the use key is released.
        ConsumedPressTracker t = new ConsumedPressTracker();
        assertFalse(t.release(1));
    }

    @Test
    void buttonsAreIndependent() {
        ConsumedPressTracker t = new ConsumedPressTracker();
        t.press(0);
        assertFalse(t.release(1));
        assertTrue(t.release(0));
    }

    @Test
    void clearForgetsPendingPresses() {
        ConsumedPressTracker t = new ConsumedPressTracker();
        t.press(0);
        t.clear();
        assertFalse(t.release(0));
    }
}
