package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.SelectionGesture.State;

class SelectionGestureTest {
    /** A panel row of 9 slots, 18 px apart, y 0-16. */
    private static int slotAt(double x, double y) {
        if (y < 0 || y >= 16 || x < 0 || x >= 9 * 18) {
            return -1;
        }
        int col = (int) (x / 18);
        return x - col * 18 < 16 ? col : -1;
    }

    private static double cx(int slot) {
        return slot * 18 + 8;
    }

    private final PanelSelection selection = new PanelSelection();
    private final SelectionGesture gesture = new SelectionGesture();

    SelectionGestureTest() {
        selection.syncContext(true, "a", 9);
    }

    private void begin(int slot) {
        gesture.beginSelect(slot, cx(slot), 8);
    }

    private void move(int slot) {
        gesture.advance(SelectionGestureTest::slotAt, selection, cx(slot), 8);
    }

    // ---- select ----

    @Test
    void beginningChangesNothingYet() {
        begin(2);
        assertEquals(State.SELECTING, gesture.state());
        assertTrue(selection.isEmpty());
    }

    @Test
    void releasingInsideTheStartSlotTogglesIt() {
        begin(2);
        gesture.release(selection);
        assertArrayEquals(new int[] {2}, selection.snapshot());
        assertEquals(State.IDLE, gesture.state());

        begin(2);
        gesture.release(selection);
        assertTrue(selection.isEmpty());
    }

    @Test
    void movingWithinTheStartSlotIsStillAClick() {
        begin(3);
        gesture.advance(SelectionGestureTest::slotAt, selection, cx(3) + 3, 10);
        gesture.release(selection);
        assertArrayEquals(new int[] {3}, selection.snapshot());
    }

    @Test
    void draggingToAnotherSlotSelectsTheStartAndEverythingPassedOver() {
        begin(1);
        move(4);
        gesture.release(selection);
        assertArrayEquals(new int[] {1, 2, 3, 4}, selection.snapshot());
    }

    @Test
    void aDragNeverTogglesTheStartOffEvenIfItWasSelected() {
        selection.addAll(List.of(1, 3));
        begin(1);
        move(2);
        gesture.release(selection);
        assertArrayEquals(new int[] {1, 2, 3}, selection.snapshot());
    }

    @Test
    void aDragOnlyAddsAndNeverRemoves() {
        selection.addAll(List.of(2, 3));
        begin(0);
        move(5);
        gesture.release(selection);
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, selection.snapshot());
    }

    @Test
    void emptySlotsAreSelectedToo() {
        // The gesture has no idea what the slots hold; every slot passed over counts.
        begin(0);
        move(2);
        gesture.release(selection);
        assertEquals(3, selection.snapshot().length);
    }

    @Test
    void aFastMoveDoesNotSkipSlotsBetweenFrames() {
        begin(0);
        move(8);
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, selection.snapshot());
    }

    @Test
    void goingBackAndForthSelectsEachSlotOnce() {
        begin(2);
        move(5);
        move(1);
        move(5);
        gesture.release(selection);
        assertArrayEquals(new int[] {1, 2, 3, 4, 5}, selection.snapshot());
    }

    @Test
    void positionsOutsideThePanelAddNothing() {
        begin(8);
        gesture.advance(SelectionGestureTest::slotAt, selection, 500, 8);
        gesture.advance(SelectionGestureTest::slotAt, selection, 500, 300);
        gesture.release(selection);
        // Never left the start slot as far as the panel is concerned: a click on it.
        assertArrayEquals(new int[] {8}, selection.snapshot());
    }

    @Test
    void cancellingKeepsWhatWasAlreadyAddedAndDropsTheUnsettledClick() {
        begin(1);
        move(3);
        gesture.cancel();
        assertEquals(State.IDLE, gesture.state());
        assertArrayEquals(new int[] {1, 2, 3}, selection.snapshot());

        begin(7); // a pending click that never settles
        gesture.cancel();
        assertFalse(selection.contains(7));
    }

    @Test
    void nothingHappensOnceTheGestureIsOver() {
        begin(1);
        gesture.release(selection);
        gesture.advance(SelectionGestureTest::slotAt, selection, cx(6), 8);
        gesture.release(selection);
        assertArrayEquals(new int[] {1}, selection.snapshot());
    }

    // ---- batch ----

    private boolean beginBatch(int slot, boolean cursorEmpty) {
        return gesture.beginBatch(slot, selection, cursorEmpty);
    }

    private int[] commit(boolean ctrl, boolean shift, boolean visible, boolean sameScreen, boolean cursorEmpty,
            java.util.function.IntPredicate hasItem) {
        return gesture.releaseBatch(ctrl, shift, visible, sameScreen, cursorEmpty, selection, hasItem);
    }

    @Test
    void aBatchOnlyStartsOnASelectedSlotWithAnEmptyCursor() {
        selection.addAll(List.of(2, 4));
        assertFalse(beginBatch(3, true)); // not selected
        assertEquals(State.IDLE, gesture.state());
        assertFalse(beginBatch(2, false)); // something on the cursor
        assertEquals(State.IDLE, gesture.state());
        assertTrue(beginBatch(2, true));
        assertEquals(State.BATCH_PENDING, gesture.state());
        assertTrue(gesture.isActive());
    }

    @Test
    void releasingGivesTheSelectedSlotsThatHoldItemsInAscendingOrder() {
        selection.addAll(List.of(7, 1, 4, 5));
        beginBatch(4, true);
        int[] slots = commit(true, false, true, true, true, i -> i != 5); // slot 5 is empty
        assertArrayEquals(new int[] {1, 4, 7}, slots);
        assertEquals(State.IDLE, gesture.state());
    }

    @Test
    void nothingIsSentWhenEveryItemIsGone() {
        selection.addAll(List.of(1, 2));
        beginBatch(1, true);
        assertEquals(0, commit(true, false, true, true, true, i -> false).length);
    }

    @Test
    void anyBrokenConditionCancelsTheBatch() {
        selection.addAll(List.of(1, 2));
        java.util.function.IntPredicate any = i -> true;

        beginBatch(1, true);
        assertEquals(0, commit(false, false, true, true, true, any).length); // Ctrl let go first
        beginBatch(1, true);
        assertEquals(0, commit(true, true, true, true, true, any).length); // Shift is down
        beginBatch(1, true);
        assertEquals(0, commit(true, false, false, true, true, any).length); // panel hidden
        beginBatch(1, true);
        assertEquals(0, commit(true, false, true, false, true, any).length); // another screen
        beginBatch(1, true);
        assertEquals(0, commit(true, false, true, true, false, any).length); // something on the cursor
        beginBatch(1, true);
        assertEquals(2, commit(true, false, true, true, true, any).length); // all fine
    }

    @Test
    void movingDuringABatchChangesNothing() {
        selection.addAll(List.of(1, 2));
        beginBatch(1, true);
        move(6);
        assertArrayEquals(new int[] {1, 2}, selection.snapshot());
        assertEquals(State.BATCH_PENDING, gesture.state());
        assertArrayEquals(new int[] {1, 2}, commit(true, false, true, true, true, i -> true));
        assertArrayEquals(new int[] {1, 2}, selection.snapshot()); // the selection is left as it was
    }

    @Test
    void slotsBeyondTheVisibleRowsStillTakePart() {
        selection.syncContext(true, "a", 108);
        selection.addAll(List.of(3, 100));
        beginBatch(3, true);
        assertArrayEquals(new int[] {3, 100}, commit(true, false, true, true, true, i -> true));
    }

    @Test
    void cancellingABatchSendsNothing() {
        selection.addAll(List.of(1));
        beginBatch(1, true);
        gesture.cancel();
        assertEquals(0, commit(true, false, true, true, true, i -> true).length);
    }
}
