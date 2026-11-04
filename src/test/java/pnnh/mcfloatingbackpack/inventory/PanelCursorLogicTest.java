package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic.DragMode;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic.Distribution;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic.Gathered;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic.PickedAll;
import pnnh.mcfloatingbackpack.inventory.QuickTransferLogicTest.FakeStack;

/** The real {@link PanelCursorLogic} over fake stacks: vanilla's drag-distribute rules, gather and pickup-all. */
class PanelCursorLogicTest {
    static final QuickTransferLogic.StackOps<FakeStack> OPS = QuickTransferLogicTest.OPS;
    static final FakeStack EMPTY = FakeStack.EMPTY;

    private static FakeStack s(int kind, int count) {
        return new FakeStack(kind, count, 64);
    }

    private static List<FakeStack> list(FakeStack... stacks) {
        return new ArrayList<>(List.of(stacks));
    }

    // ---- canQuickReplace / isValidDragSlot / placeCount ----

    @Test
    void emptyAndSameKindSlotsAreReplaceableOthersAreNot() {
        FakeStack cursor = s(1, 10);
        assertTrue(PanelCursorLogic.canQuickReplace(OPS, EMPTY, cursor));
        assertTrue(PanelCursorLogic.canQuickReplace(OPS, s(1, 30), cursor));
        assertFalse(PanelCursorLogic.canQuickReplace(OPS, s(2, 30), cursor));
    }

    @Test
    void aFullSameKindStackStillCountsAsAValidSlotLikeVanilla() {
        // vanilla's canItemQuickReplace(slot, stack, true) only checks count <= maxStack, so a full stack
        // takes a share of the split even though nothing fits into it
        assertTrue(PanelCursorLogic.canQuickReplace(OPS, s(1, 64), s(1, 10)));
        assertTrue(PanelCursorLogic.isValidDragSlot(OPS, s(1, 64), s(1, 10), 0));
    }

    @Test
    void aSlotOnlyJoinsWhileTheCursorHasMoreItemsThanSelectedSlots() {
        FakeStack cursor = s(1, 3);
        assertTrue(PanelCursorLogic.isValidDragSlot(OPS, EMPTY, cursor, 0));
        assertTrue(PanelCursorLogic.isValidDragSlot(OPS, EMPTY, cursor, 2));
        assertFalse(PanelCursorLogic.isValidDragSlot(OPS, EMPTY, cursor, 3));
    }

    @Test
    void placeCountIsFloorForLeftAndOneForRight() {
        assertEquals(15, PanelCursorLogic.placeCount(DragMode.LEFT, 60, 4));
        assertEquals(3, PanelCursorLogic.placeCount(DragMode.LEFT, 10, 3));
        assertEquals(1, PanelCursorLogic.placeCount(DragMode.RIGHT, 10, 5));
        assertEquals(1, PanelCursorLogic.placeCount(DragMode.RIGHT, 64, 2));
    }

    // ---- distribute ----

    @Test
    void sixtyOverFourEmptySlotsGivesFifteenEach() {
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.LEFT, s(1, 60), list(EMPTY, EMPTY, EMPTY, EMPTY), 64);
        assertFalse(d.rejected());
        for (FakeStack st : d.stacks()) {
            assertEquals(15, st.count());
        }
        assertTrue(OPS.isEmpty(d.cursorLeft()));
    }

    @Test
    void tenOverThreeGivesThreeEachAndOneStaysOnTheCursor() {
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.LEFT, s(1, 10), list(EMPTY, EMPTY, EMPTY), 64);
        assertEquals(3, d.stacks().get(0).count());
        assertEquals(3, d.stacks().get(2).count());
        assertEquals(1, d.cursorLeft().count());
    }

    @Test
    void existingStackOnlyReceivesWhatFitsAndTheRestStaysOnTheCursor() {
        // 64 over 3 slots: 21 each, but the slot already holding 60 can only take 4
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.LEFT, s(1, 64), list(s(1, 60), EMPTY, EMPTY), 64);
        assertEquals(64, d.stacks().get(0).count());
        assertEquals(21, d.stacks().get(1).count());
        assertEquals(21, d.stacks().get(2).count());
        assertTrue(d.capped().get(0));
        assertEquals(64 - 4 - 21 - 21, d.cursorLeft().count());
    }

    @Test
    void aFullSlotTakesAShareOfTheSplitButGainsNothing() {
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.LEFT, s(1, 10), list(s(1, 64), EMPTY), 64);
        assertEquals(64, d.stacks().get(0).count());
        assertEquals(5, d.stacks().get(1).count());
        assertEquals(5, d.cursorLeft().count());
    }

    @Test
    void rightButtonPlacesOneInEverySlot() {
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.RIGHT, s(1, 10), list(EMPTY, EMPTY, EMPTY, EMPTY, EMPTY), 64);
        for (FakeStack st : d.stacks()) {
            assertEquals(1, st.count());
        }
        assertEquals(5, d.cursorLeft().count());
    }

    @Test
    void notEnoughItemsForEverySlotRejectsTheWholeThing() {
        // vanilla requires cursor count >= number of slots at settle time
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.LEFT, s(1, 2), list(EMPTY, EMPTY, EMPTY), 64);
        assertTrue(d.rejected());
        assertEquals(2, d.cursorLeft().count());
        for (FakeStack st : d.stacks()) {
            assertTrue(OPS.isEmpty(st));
        }
    }

    @Test
    void differentKindSlotsAreLeftAlone() {
        FakeStack other = s(2, 7);
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.LEFT, s(1, 10), list(other, EMPTY, EMPTY), 64);
        // callers only pass valid slots (selection filters them); if one slips through it is skipped but, as in
        // vanilla's settle step, still counts towards the number of shares: floor(10 / 3) = 3
        assertEquals(other, d.stacks().get(0));
        assertEquals(3, d.stacks().get(1).count());
        assertEquals(3, d.stacks().get(2).count());
    }

    @Test
    void slotLimitCapsEachSlotBelowTheItemMax() {
        Distribution<FakeStack> d = PanelCursorLogic.distribute(
                OPS, DragMode.LEFT, s(1, 64), list(EMPTY, EMPTY), 16);
        assertEquals(16, d.stacks().get(0).count());
        assertEquals(16, d.stacks().get(1).count());
        assertEquals(32, d.cursorLeft().count());
    }

    // ---- gather (drag) ----

    @Test
    void gatheringTakesSameKindUpToTheMaxStack() {
        FakeStack cursor = s(1, 20);
        Gathered<FakeStack> g1 = PanelCursorLogic.gather(OPS, s(1, 30), cursor);
        assertEquals(30, g1.moved());
        assertTrue(OPS.isEmpty(g1.slot()));
        assertEquals(50, g1.cursor().count());
        Gathered<FakeStack> g2 = PanelCursorLogic.gather(OPS, s(1, 40), g1.cursor());
        assertEquals(14, g2.moved());
        assertEquals(26, g2.slot().count());
        assertEquals(64, g2.cursor().count());
    }

    @Test
    void gatheringIgnoresOtherKindsEmptySlotsAndAFullCursor() {
        assertEquals(0, PanelCursorLogic.gather(OPS, s(2, 30), s(1, 20)).moved());
        assertEquals(0, PanelCursorLogic.gather(OPS, EMPTY, s(1, 20)).moved());
        Gathered<FakeStack> full = PanelCursorLogic.gather(OPS, s(1, 30), s(1, 64));
        assertEquals(0, full.moved());
        assertEquals(30, full.slot().count());
    }

    // ---- pickup all (double click) ----

    @Test
    void pickupAllTakesPartialStacksFirstThenFullOnes() {
        // cursor 10, slots: 20, 30, 64, (clicked) empty
        List<FakeStack> slots = list(s(1, 20), s(1, 30), s(1, 64), EMPTY);
        PickedAll<FakeStack> p = PanelCursorLogic.pickupAll(OPS, s(1, 10), slots);
        assertEquals(64, p.cursor().count());
        // first pass: 20 and 30 are taken whole (cursor 60), the full stack is skipped; second pass tops up from it
        assertTrue(OPS.isEmpty(p.slots().get(0)));
        assertTrue(OPS.isEmpty(p.slots().get(1)));
        assertEquals(60, p.slots().get(2).count());
        assertEquals(54, p.moved());
    }

    @Test
    void pickupAllLeavesTheRestWhenTheCursorFillsUp() {
        PickedAll<FakeStack> p = PanelCursorLogic.pickupAll(OPS, s(1, 60), list(s(1, 10), s(1, 10), EMPTY));
        assertEquals(64, p.cursor().count());
        assertEquals(6, p.slots().get(0).count());
        assertEquals(10, p.slots().get(1).count());
    }

    @Test
    void pickupAllIgnoresOtherKinds() {
        PickedAll<FakeStack> p = PanelCursorLogic.pickupAll(OPS, s(1, 10), list(s(2, 20), EMPTY));
        assertEquals(0, p.moved());
        assertEquals(10, p.cursor().count());
    }

    // ---- conservation ----

    @Test
    void totalsAreConservedOnRandomLayouts() {
        Random random = new Random(20261105L);
        int[] max = {1, 16, 64};
        for (int round = 0; round < 100; round++) {
            int n = 2 + random.nextInt(8);
            List<FakeStack> slots = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (random.nextInt(3) > 0) {
                    int kind = 1 + random.nextInt(3);
                    int m = max[kind - 1];
                    slots.add(new FakeStack(kind, 1 + random.nextInt(m), m));
                } else {
                    slots.add(EMPTY);
                }
            }
            int kind = 1 + random.nextInt(3);
            int m = max[kind - 1];
            FakeStack cursor = new FakeStack(kind, 1 + random.nextInt(m), m);
            int before = total(slots, kind) + cursor.count();

            Distribution<FakeStack> d = PanelCursorLogic.distribute(
                    OPS, random.nextBoolean() ? DragMode.LEFT : DragMode.RIGHT, cursor, slots, 64);
            assertEquals(before, total(d.stacks(), kind) + d.cursorLeft().count(), "distribute round " + round);

            PickedAll<FakeStack> p = PanelCursorLogic.pickupAll(OPS, cursor, slots);
            assertEquals(before, total(p.slots(), kind) + p.cursor().count(), "pickupAll round " + round);

            FakeStack c = cursor;
            List<FakeStack> after = new ArrayList<>(slots);
            for (int i = 0; i < n; i++) {
                Gathered<FakeStack> g = PanelCursorLogic.gather(OPS, after.get(i), c);
                after.set(i, g.slot());
                c = g.cursor();
                assertTrue(c.count() <= c.max());
            }
            assertEquals(before, total(after, kind) + c.count(), "gather round " + round);
        }
    }

    private static int total(List<FakeStack> stacks, int kind) {
        int n = 0;
        for (FakeStack st : stacks) {
            if (!OPS.isEmpty(st) && st.kind() == kind) {
                n += st.count();
            }
        }
        return n;
    }
}
