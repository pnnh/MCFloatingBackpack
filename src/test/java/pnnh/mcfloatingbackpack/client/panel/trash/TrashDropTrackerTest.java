package pnnh.mcfloatingbackpack.client.panel.trash;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 021: remembering where each mouse button went down, so a release on the trash can panel can tell a drop from a click. */
class TrashDropTrackerTest {
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int MIDDLE = 2;

    @Test
    void pressOutsideThenReleaseOnTheTrashIsADrop() {
        TrashDropTracker tracker = new TrashDropTracker();
        tracker.press(LEFT, false);
        assertTrue(tracker.release(LEFT, true));
    }

    @Test
    void pressOnTheTrashIsNeverADrop() {
        TrashDropTracker tracker = new TrashDropTracker();
        tracker.press(LEFT, true);
        assertFalse(tracker.release(LEFT, true));
    }

    @Test
    void releaseOutsideIsNotADrop() {
        TrashDropTracker tracker = new TrashDropTracker();
        tracker.press(LEFT, false);
        assertFalse(tracker.release(LEFT, false));
    }

    @Test
    void onlyTheLeftButtonDrops() {
        TrashDropTracker tracker = new TrashDropTracker();
        tracker.press(RIGHT, false);
        tracker.press(MIDDLE, false);
        assertFalse(tracker.release(RIGHT, true));
        assertFalse(tracker.release(MIDDLE, true));
    }

    @Test
    void aReleaseForgetsThePress() {
        TrashDropTracker tracker = new TrashDropTracker();
        tracker.press(LEFT, false);
        assertTrue(tracker.release(LEFT, true));
        assertFalse(tracker.release(LEFT, true));
    }

    @Test
    void buttonsDoNotAffectEachOther() {
        TrashDropTracker tracker = new TrashDropTracker();
        tracker.press(LEFT, false);
        tracker.press(RIGHT, true);
        assertFalse(tracker.release(RIGHT, true));
        assertTrue(tracker.release(LEFT, true));
    }

    @Test
    void aNewPressReplacesTheOldRecord() {
        TrashDropTracker tracker = new TrashDropTracker();
        tracker.press(LEFT, false);
        tracker.press(LEFT, true);
        assertFalse(tracker.release(LEFT, true));
    }

    @Test
    void clearForgetsEverythingAndAnUnrecordedReleaseIsNotADrop() {
        TrashDropTracker tracker = new TrashDropTracker();
        assertFalse(tracker.release(LEFT, true));
        tracker.press(LEFT, false);
        tracker.clear();
        assertFalse(tracker.release(LEFT, true));
    }
}
