package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Kind;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Release;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.State;
import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

class PanelDragGestureTest {
    record FakeStack(int kind, int count, int max) {
        static final FakeStack EMPTY = new FakeStack(0, 0, 64);
    }

    static final QuickTransferLogic.StackOps<FakeStack> OPS = new QuickTransferLogic.StackOps<>() {
        @Override
        public FakeStack empty() {
            return FakeStack.EMPTY;
        }

        @Override
        public boolean isEmpty(FakeStack s) {
            return s.count() <= 0;
        }

        @Override
        public int count(FakeStack s) {
            return s.count();
        }

        @Override
        public int maxStack(FakeStack s) {
            return s.max();
        }

        @Override
        public boolean sameKind(FakeStack a, FakeStack b) {
            return a.kind() == b.kind();
        }

        @Override
        public FakeStack withCount(FakeStack s, int count) {
            return count <= 0 ? FakeStack.EMPTY : new FakeStack(s.kind(), count, s.max());
        }
    };

    static final FakeStack EMPTY = FakeStack.EMPTY;

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

    private static FakeStack s(int kind, int count) {
        return new FakeStack(kind, count, 64);
    }

    private final FakeStack[] slots = new FakeStack[9];
    private final PanelDragGesture<FakeStack> g = new PanelDragGesture<>(OPS, 64);

    PanelDragGestureTest() {
        java.util.Arrays.fill(slots, EMPTY);
    }

    private boolean press(int slot, boolean right, FakeStack cursor, long now) {
        return g.beginPress(slot, right, cursor, i -> slots[i], cx(slot), 8, now);
    }

    private List<Integer> move(FakeStack cursor, int toSlot) {
        return g.advance(PanelDragGestureTest::slotAt, i -> slots[i], cursor, cx(toSlot), 8);
    }

    // ---- distributing ----

    @Test
    void pressWithAnItemOnTheCursorStartsADragAndSendsNothingYet() {
        assertTrue(press(0, false, s(1, 60), 0));
        assertEquals(State.DISTRIBUTING, g.state());
        assertTrue(g.isActive());
    }

    @Test
    void pressWithAnEmptyCursorIsNotHandledSoTheNormalPickupHappens() {
        slots[0] = s(1, 20);
        assertFalse(press(0, false, EMPTY, 0)); // caller sends the ordinary click
        assertEquals(State.GATHERING, g.state());
        // right button pickup of half a stack never starts a gather
        PanelDragGesture<FakeStack> g2 = new PanelDragGesture<>(OPS, 64);
        assertFalse(g2.beginPress(0, true, EMPTY, i -> slots[i], 8, 8, 0));
        assertEquals(State.IDLE, g2.state());
    }

    @Test
    void releaseOverSeveralValidSlotsSettlesTheDragInOrder() {
        FakeStack cursor = s(1, 60);
        press(0, false, cursor, 0);
        move(cursor, 3);
        Release r = g.release(3);
        assertEquals(Kind.DISTRIBUTE, r.kind());
        assertEquals(List.of(0, 1, 2, 3), r.slots());
        assertFalse(r.right());
        assertEquals(State.IDLE, g.state());
    }

    @Test
    void releaseOverOnlyOneSlotIsAnOrdinaryClickOnThatSlot() {
        FakeStack cursor = s(1, 60);
        press(2, false, cursor, 0);
        Release r = g.release(2);
        assertEquals(Kind.CLICK, r.kind());
        assertEquals(List.of(2), r.slots());
    }

    @Test
    void rightButtonKeepsTheButtonForSingleSlotClicksAndDistribution() {
        FakeStack cursor = s(1, 10);
        press(0, true, cursor, 0);
        move(cursor, 4);
        Release r = g.release(4);
        assertEquals(Kind.DISTRIBUTE, r.kind());
        assertTrue(r.right());
        press(5, true, cursor, 1000);
        Release single = g.release(5);
        assertEquals(Kind.CLICK, single.kind());
        assertTrue(single.right());
    }

    @Test
    void whenNoSlotWasValidTheReleaseSlotGetsTheClick() {
        slots[0] = s(2, 5); // different kind: not a valid drag slot, a click on it would swap
        FakeStack cursor = s(1, 60);
        press(0, false, cursor, 0);
        Release r = g.release(0);
        assertEquals(Kind.CLICK, r.kind());
        assertEquals(List.of(0), r.slots());
        press(0, false, cursor, 5000);
        assertEquals(Kind.NONE, g.release(-1).kind());
    }

    @Test
    void invalidAndAlreadyVisitedSlotsAreNotSelected() {
        slots[1] = s(2, 5);
        FakeStack cursor = s(1, 60);
        press(0, false, cursor, 0);
        move(cursor, 2); // passes slot 1 (different kind) and ends on slot 2
        move(cursor, 0); // back over slot 1 and slot 0: nothing new
        Release r = g.release(0);
        assertEquals(List.of(0, 2), r.slots());
    }

    @Test
    void fastMovesDoNotSkipSlotsBetweenFrames() {
        FakeStack cursor = s(1, 60);
        press(0, false, cursor, 0);
        move(cursor, 8);
        assertEquals(9, g.release(8).slots().size());
    }

    @Test
    void aSlotOnlyJoinsWhileTheCursorHoldsMoreItemsThanSelectedSlots() {
        FakeStack cursor = s(1, 3);
        press(0, false, cursor, 0);
        move(cursor, 8);
        assertEquals(List.of(0, 1, 2), g.release(8).slots());
    }

    @Test
    void previewShowsNothingForASingleSlotAndTheSplitForSeveral() {
        FakeStack cursor = s(1, 10);
        press(0, false, cursor, 0);
        assertNull(g.preview(cursor, i -> slots[i]));
        move(cursor, 2);
        var p = g.preview(cursor, i -> slots[i]);
        assertNotNull(p);
        assertEquals(List.of(0, 1, 2), p.slots());
        assertEquals(3, p.stacks().get(0).count());
        assertEquals(1, p.cursorLeft().count());
    }

    @Test
    void cancelDropsTheDragWithoutAnyRequest() {
        FakeStack cursor = s(1, 60);
        press(0, false, cursor, 0);
        move(cursor, 3);
        g.cancel();
        assertFalse(g.isActive());
        assertEquals(Kind.NONE, g.release(3).kind());
        assertNull(g.preview(cursor, i -> slots[i]));
    }

    // ---- gathering ----

    @Test
    void gatherCollectsOnlyNewSameKindSlotsAfterThePickup() {
        slots[0] = s(1, 20);
        slots[1] = s(1, 30);
        slots[2] = s(2, 5);
        slots[3] = EMPTY;
        slots[4] = s(1, 40);
        assertFalse(press(0, false, EMPTY, 0));
        List<Integer> got = move(s(1, 20), 4);
        assertEquals(List.of(1, 4), got); // slot 2 (other kind) and slot 3 (empty) ignored, start slot not a candidate
        assertTrue(move(s(1, 64), 4).isEmpty());
        assertTrue(move(s(1, 64), 1).isEmpty()); // already visited
        assertEquals(Kind.NONE, g.release(1).kind());
        assertEquals(State.IDLE, g.state());
    }

    @Test
    void cancellingAGatherKeepsWhatWasCollectedAndStops() {
        slots[0] = s(1, 20);
        slots[1] = s(1, 30);
        press(0, false, EMPTY, 0);
        move(s(1, 20), 1);
        g.cancel();
        assertEquals(State.IDLE, g.state());
        assertTrue(move(s(1, 50), 5).isEmpty());
    }

    // ---- double click ----

    @Test
    void secondClickOnTheSameSlotWithinTheWindowGathersEverythingOnRelease() {
        slots[2] = s(1, 20);
        press(2, false, EMPTY, 1000);         // first click picks the stack up (not handled here)
        g.release(2);
        slots[2] = EMPTY;                      // the slot is empty once the pickup synced back
        assertTrue(press(2, false, s(1, 20), 1200)); // second click within 250 ms
        Release r = g.release(2);
        assertEquals(Kind.GATHER_ALL, r.kind());
        assertEquals(List.of(2), r.slots());
    }

    @Test
    void slowSecondClickOtherSlotOrOtherButtonIsNotADoubleClick() {
        press(2, false, EMPTY, 1000);
        g.release(2);
        press(2, false, s(1, 20), 1000 + PanelDragGesture.DOUBLE_CLICK_MS); // exactly at the limit: too late
        assertEquals(Kind.CLICK, g.release(2).kind());

        press(3, false, EMPTY, 5000);
        g.release(3);
        press(4, false, s(1, 20), 5100);                                   // other slot
        assertEquals(Kind.CLICK, g.release(4).kind());

        press(5, false, EMPTY, 9000);
        g.release(5);
        press(5, true, s(1, 20), 9100);                                    // other button
        assertEquals(Kind.CLICK, g.release(5).kind());
    }

    @Test
    void doubleClickThenReleasingOnAnotherSlotIsNotAGatherAll() {
        press(2, false, EMPTY, 1000);
        g.release(2);
        press(2, false, s(1, 20), 1100);
        move(s(1, 20), 6);
        assertEquals(Kind.DISTRIBUTE, g.release(6).kind());
    }
}
