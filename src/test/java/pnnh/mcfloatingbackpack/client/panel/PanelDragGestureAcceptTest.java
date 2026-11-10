package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Acceptor;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Kind;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Release;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGestureTest.FakeStack;

/** 037: a drag only selects the slots the acceptor says yes to (a row of the category extensions takes only its category). */
class PanelDragGestureAcceptTest {
    private static final FakeStack EMPTY = FakeStack.EMPTY;

    private final FakeStack[] slots = new FakeStack[9];
    private final PanelDragGesture<FakeStack> g = new PanelDragGesture<>(PanelDragGestureTest.OPS, 64);

    PanelDragGestureAcceptTest() {
        java.util.Arrays.fill(slots, EMPTY);
    }

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

    /** Slots 3..5 refuse the cursor's stack. */
    private static final Acceptor<FakeStack> NOT_3_TO_5 = (slot, cursor) -> slot < 3 || slot > 5;

    private boolean press(int slot, FakeStack cursor, Acceptor<FakeStack> accepts) {
        return g.beginPress(slot, false, cursor, i -> slots[i], accepts, cx(slot), 8, 0);
    }

    private void move(FakeStack cursor, int toSlot, Acceptor<FakeStack> accepts) {
        g.advance(PanelDragGestureAcceptTest::slotAt, i -> slots[i], accepts, cursor, cx(toSlot), 8);
    }

    @Test
    void aRefusedSlotIsNeverSelected() {
        FakeStack cursor = s(1, 60);
        press(0, cursor, NOT_3_TO_5);
        move(cursor, 7, NOT_3_TO_5);
        assertEquals(List.of(0, 1, 2, 6, 7), g.selectedSlots());
    }

    @Test
    void theReleaseDistributesOverTheAcceptedSlotsOnly() {
        FakeStack cursor = s(1, 60);
        press(0, cursor, NOT_3_TO_5);
        move(cursor, 7, NOT_3_TO_5);
        Release r = g.release(7);
        assertEquals(Kind.DISTRIBUTE, r.kind());
        assertEquals(List.of(0, 1, 2, 6, 7), r.slots());
    }

    @Test
    void theRefusedSlotsAreNotInThePreview() {
        FakeStack cursor = s(1, 60);
        press(0, cursor, NOT_3_TO_5);
        move(cursor, 7, NOT_3_TO_5);
        PanelDragGesture.Preview<FakeStack> preview = g.preview(cursor, i -> slots[i]);
        assertEquals(List.of(0, 1, 2, 6, 7), preview.slots());
    }

    @Test
    void aPressOnARefusedSlotSelectsNothingAtFirst() {
        FakeStack cursor = s(1, 60);
        press(4, cursor, NOT_3_TO_5);
        assertTrue(g.selectedSlots().isEmpty());
        move(cursor, 6, NOT_3_TO_5);
        assertEquals(List.of(6), g.selectedSlots());
    }

    @Test
    void withOnlyOneAcceptedSlotTheReleaseIsAClickOnThatSlot() {
        FakeStack cursor = s(1, 60);
        press(2, cursor, NOT_3_TO_5);
        move(cursor, 4, NOT_3_TO_5);
        Release r = g.release(4);
        assertEquals(Kind.CLICK, r.kind());
        assertEquals(List.of(2), r.slots());
    }

    @Test
    void withNoAcceptedSlotTheReleaseFallsBackToAClickUnderTheMouse() {
        // The caller's click pre-check stops it; the gesture itself only reports where the button came up.
        FakeStack cursor = s(1, 60);
        press(4, cursor, NOT_3_TO_5);
        Release r = g.release(4);
        assertEquals(Kind.CLICK, r.kind());
        assertEquals(List.of(4), r.slots());
    }

    @Test
    void anAlwaysAcceptingAcceptorBehavesLikeTheOldMethods() {
        FakeStack cursor = s(1, 60);
        PanelDragGesture<FakeStack> old = new PanelDragGesture<>(PanelDragGestureTest.OPS, 64);
        old.beginPress(0, false, cursor, i -> slots[i], cx(0), 8, 0);
        old.advance(PanelDragGestureAcceptTest::slotAt, i -> slots[i], cursor, cx(7), 8);
        press(0, cursor, (slot, c) -> true);
        move(cursor, 7, (slot, c) -> true);
        assertEquals(old.selectedSlots(), g.selectedSlots());
        assertNull(old.preview(EMPTY, i -> slots[i]));
    }
}
