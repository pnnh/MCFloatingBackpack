package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.ContainerToBackpack.Result;
import pnnh.mcfloatingbackpack.inventory.QuickTransferLogicTest.FakeStack;

/** Runs the real {@link ContainerToBackpack} over fake slots (no Minecraft bootstrap needed). */
class ContainerToBackpackTest {
    static final QuickTransferLogic.StackOps<FakeStack> OPS = QuickTransferLogicTest.OPS;

    /** A slot like vanilla's: whole/partial take, safeInsert-style put back, optional refusals. */
    static final class FakeSlot implements ContainerToBackpack.SlotAccess<FakeStack> {
        FakeStack stack;
        boolean eligible = true;       // mayPickup && mayPlace(own item)
        boolean refusesInsert;         // safeInsert returns the stack untouched
        int slotLimit = 64;            // how much safeInsert can hold

        FakeSlot(FakeStack stack) {
            this.stack = stack;
        }

        @Override
        public FakeStack get() {
            return stack;
        }

        @Override
        public boolean canTakeAndPlaceBack() {
            return eligible;
        }

        @Override
        public FakeStack take(int count) {
            FakeStack removed = OPS.withCount(stack, Math.min(count, stack.count()));
            stack = OPS.withCount(stack, stack.count() - removed.count());
            return removed;
        }

        @Override
        public FakeStack insert(FakeStack in) {
            if (refusesInsert || OPS.isEmpty(in)) {
                return in;
            }
            if (OPS.isEmpty(stack)) {
                int put = Math.min(in.count(), slotLimit);
                stack = OPS.withCount(in, put);
                return OPS.withCount(in, in.count() - put);
            }
            if (!OPS.sameKind(stack, in)) {
                return in;
            }
            int put = Math.min(in.count(), Math.max(0, slotLimit - stack.count()));
            stack = OPS.withCount(stack, stack.count() + put);
            return OPS.withCount(in, in.count() - put);
        }

        @Override
        public void set(FakeStack s) {
            stack = s;
        }
    }

    static final class Fallback implements ContainerToBackpack.Fallback<FakeStack> {
        int inventoryCapacity;
        final List<FakeStack> placed = new ArrayList<>();
        final List<FakeStack> dropped = new ArrayList<>();

        Fallback(int inventoryCapacity) {
            this.inventoryCapacity = inventoryCapacity;
        }

        @Override
        public FakeStack toInventory(FakeStack s) {
            int put = Math.min(s.count(), inventoryCapacity);
            inventoryCapacity -= put;
            if (put > 0) {
                placed.add(OPS.withCount(s, put));
            }
            return OPS.withCount(s, s.count() - put);
        }

        @Override
        public void drop(FakeStack s) {
            dropped.add(s);
        }

        int total() {
            int n = 0;
            for (FakeStack s : placed) {
                n += s.count();
            }
            for (FakeStack s : dropped) {
                n += s.count();
            }
            return n;
        }
    }

    private static FakeStack[] backpack(int n) {
        FakeStack[] b = new FakeStack[n];
        java.util.Arrays.fill(b, FakeStack.EMPTY);
        return b;
    }

    private static int[] order(int n) {
        int[] o = new int[n];
        for (int i = 0; i < n; i++) {
            o[i] = i;
        }
        return o;
    }

    private static Result take(FakeSlot slot, FakeStack[] backpack, Fallback fallback) {
        return ContainerToBackpack.take(OPS, slot, order(backpack.length), i -> backpack[i], (s, i) -> backpack[i] = s, fallback);
    }

    private static int total(FakeStack[] b, int kind) {
        int n = 0;
        for (FakeStack s : b) {
            if (!OPS.isEmpty(s) && s.kind() == kind) {
                n += s.count();
            }
        }
        return n;
    }

    @Test
    void wholeStackMovesIntoTheBackpackAndTheSlotIsEmptied() {
        FakeSlot slot = new FakeSlot(new FakeStack(1, 40, 64));
        FakeStack[] bp = backpack(3);
        Result r = take(slot, bp, new Fallback(36));
        assertEquals(40, r.moved());
        assertTrue(OPS.isEmpty(slot.stack));
        assertEquals(40, total(bp, 1));
        assertFalse(r.skipped());
    }

    @Test
    void mergesIntoExistingStackFirst() {
        FakeSlot slot = new FakeSlot(new FakeStack(1, 20, 64));
        FakeStack[] bp = backpack(3);
        bp[2] = new FakeStack(1, 40, 64);
        take(slot, bp, new Fallback(36));
        assertEquals(60, bp[2].count());
        assertTrue(OPS.isEmpty(bp[0]));
    }

    @Test
    void fullBackpackPutsEverythingBackIntoTheSlot() {
        FakeSlot slot = new FakeSlot(new FakeStack(1, 10, 64));
        FakeStack[] bp = backpack(2);
        bp[0] = new FakeStack(2, 64, 64);
        bp[1] = new FakeStack(2, 64, 64);
        Fallback fb = new Fallback(36);
        Result r = take(slot, bp, fb);
        assertEquals(0, r.moved());
        assertEquals(10, slot.stack.count());
        assertEquals(0, fb.total());
        assertFalse(r.usedFallback());
    }

    @Test
    void partialFitLeavesTheRemainderInTheSlot() {
        FakeSlot slot = new FakeSlot(new FakeStack(1, 10, 64));
        FakeStack[] bp = backpack(1);
        bp[0] = new FakeStack(1, 60, 64);
        Result r = take(slot, bp, new Fallback(36));
        assertEquals(4, r.moved());
        assertEquals(64, bp[0].count());
        assertEquals(6, slot.stack.count());
    }

    @Test
    void bigStackOnlyMovesWhatTheBackpackCanHold() {
        // a Sophisticated-style slot holding far more than 64
        FakeSlot slot = new FakeSlot(new FakeStack(1, 1000, 64));
        slot.slotLimit = 100000;
        FakeStack[] bp = backpack(2);
        Result r = take(slot, bp, new Fallback(36));
        assertEquals(128, r.moved());
        assertEquals(872, slot.stack.count());
        assertEquals(128, total(bp, 1));
    }

    @Test
    void ineligibleSlotsAreSkippedAndLeftUntouched() {
        FakeSlot output = new FakeSlot(new FakeStack(1, 5, 64));
        output.eligible = false; // e.g. a furnace output: mayPlace is false for every stack
        FakeStack[] bp = backpack(2);
        Result r = take(output, bp, new Fallback(36));
        assertTrue(r.skipped());
        assertEquals(5, output.stack.count());
        assertEquals(0, total(bp, 1));
    }

    @Test
    void emptySlotIsSkipped() {
        FakeSlot slot = new FakeSlot(FakeStack.EMPTY);
        Result r = take(slot, backpack(2), new Fallback(36));
        assertTrue(r.skipped());
    }

    @Test
    void slotThatRefusesTheRemainderFallsBackToTheInventoryWithoutLosingAnything() {
        FakeSlot slot = new FakeSlot(new FakeStack(1, 10, 64));
        slot.refusesInsert = true;
        FakeStack[] bp = backpack(1);
        bp[0] = new FakeStack(1, 60, 64);
        Fallback fb = new Fallback(36);
        Result r = take(slot, bp, fb);
        assertTrue(r.usedFallback());
        assertEquals(4, r.moved());
        // slot is empty after the take, so the remainder can simply be set back
        assertEquals(6, slot.stack.count() + fb.total());
        assertEquals(10, total(bp, 1) - 60 + slot.stack.count() + fb.total());
    }

    @Test
    void lastResortDropsWhatNothingCanHold() {
        FakeSlot slot = new FakeSlot(new FakeStack(1, 10, 64));
        slot.refusesInsert = true;
        FakeStack[] bp = backpack(1);
        bp[0] = new FakeStack(1, 60, 64);
        // simulate a slot that was refilled by an onTake side effect so set() is not allowed:
        Fallback fb = new Fallback(0);
        ContainerToBackpack.SlotAccess<FakeStack> blocked = new ContainerToBackpack.SlotAccess<>() {
            @Override
            public FakeStack get() {
                return slot.stack.count() > 6 ? slot.stack : new FakeStack(9, 1, 64); // looks occupied by something else
            }

            @Override
            public boolean canTakeAndPlaceBack() {
                return true;
            }

            @Override
            public FakeStack take(int count) {
                return slot.take(count);
            }

            @Override
            public FakeStack insert(FakeStack in) {
                return in;
            }

            @Override
            public void set(FakeStack s) {
                // refused: slot is occupied by a foreign stack
            }
        };
        Result r = ContainerToBackpack.take(OPS, blocked, order(1), i -> bp[i], (s, i) -> bp[i] = s, fb);
        assertTrue(r.dropped());
        assertEquals(6, fb.total());
    }

    @Test
    void totalsAreConservedOnRandomLayouts() {
        Random random = new Random(20261105L);
        int[] max = {1, 16, 64};
        for (int round = 0; round < 100; round++) {
            int n = 1 + random.nextInt(10);
            FakeStack[] bp = backpack(n);
            for (int i = 0; i < n; i++) {
                if (random.nextInt(3) > 0) {
                    int kind = 1 + random.nextInt(3);
                    int m = max[kind - 1];
                    bp[i] = new FakeStack(kind, 1 + random.nextInt(m), m);
                }
            }
            int kind = 1 + random.nextInt(3);
            int m = max[kind - 1];
            FakeSlot slot = new FakeSlot(new FakeStack(kind, 1 + random.nextInt(500), m));
            slot.slotLimit = random.nextBoolean() ? 100000 : m;
            slot.refusesInsert = random.nextInt(4) == 0;
            Fallback fb = new Fallback(random.nextInt(40));

            int before = total(bp, kind) + slot.stack.count();
            take(slot, bp, fb);
            int after = total(bp, kind) + slot.stack.count() + fb.total();
            assertEquals(before, after, "round " + round);
        }
    }
}
