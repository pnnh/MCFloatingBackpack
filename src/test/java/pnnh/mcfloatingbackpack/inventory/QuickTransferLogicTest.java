package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Runs the real {@link QuickTransferLogic} algorithm over a fake stack type, so no Minecraft
 * bootstrap is needed and the code under test is the production code (not a mirror of it).
 */
class QuickTransferLogicTest {
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

    private static FakeStack[] slots(int n) {
        FakeStack[] a = new FakeStack[n];
        Arrays.fill(a, FakeStack.EMPTY);
        return a;
    }

    private static int[] order(int n) {
        int[] o = new int[n];
        for (int i = 0; i < n; i++) {
            o[i] = i;
        }
        return o;
    }

    private static FakeStack move(FakeStack source, FakeStack[] target, int[] targetOrder) {
        return QuickTransferLogic.moveInto(OPS, source, targetOrder, i -> target[i], (s, i) -> target[i] = s);
    }

    @Test
    void wholeStackMergesIntoExistingSameKind() {
        FakeStack[] target = slots(3);
        target[1] = new FakeStack(1, 40, 64);
        FakeStack rest = move(new FakeStack(1, 20, 64), target, order(3));
        assertTrue(rest.count() == 0);
        assertEquals(60, target[1].count());
        assertTrue(OPS.isEmpty(target[0]));
        assertTrue(OPS.isEmpty(target[2]));
    }

    @Test
    void overflowAfterMergeGoesIntoEmptySlot() {
        FakeStack[] target = slots(3);
        target[0] = new FakeStack(1, 60, 64);
        FakeStack rest = move(new FakeStack(1, 20, 64), target, order(3));
        assertTrue(OPS.isEmpty(rest));
        assertEquals(64, target[0].count());
        assertEquals(16, target[1].count());
    }

    @Test
    void remainderStaysWhenNoRoom() {
        FakeStack[] target = slots(2);
        target[0] = new FakeStack(1, 64, 64);
        target[1] = new FakeStack(2, 5, 64);
        FakeStack rest = move(new FakeStack(1, 10, 64), target, order(2));
        assertEquals(10, rest.count());
        assertEquals(64, target[0].count());
        assertEquals(5, target[1].count());
    }

    @Test
    void unstackableItemOnlyFillsEmptySlots() {
        FakeStack[] target = slots(3);
        target[0] = new FakeStack(7, 1, 1);
        FakeStack rest = move(new FakeStack(7, 1, 1), target, order(3));
        assertTrue(OPS.isEmpty(rest));
        assertEquals(1, target[0].count());
        assertEquals(1, target[1].count());
    }

    @Test
    void differentKindsDoNotMerge() {
        FakeStack[] target = slots(2);
        target[0] = new FakeStack(2, 10, 64);
        FakeStack rest = move(new FakeStack(1, 10, 64), target, order(2));
        assertTrue(OPS.isEmpty(rest));
        assertEquals(10, target[0].count());
        assertEquals(1, target[1].kind());
    }

    @Test
    void mergePassRunsBeforeEmptyPassRegardlessOfOrder() {
        // Slot 0 is empty and comes first, but the same-kind stack in slot 2 must be topped up first.
        FakeStack[] target = slots(3);
        target[2] = new FakeStack(1, 10, 64);
        FakeStack rest = move(new FakeStack(1, 5, 64), target, order(3));
        assertTrue(OPS.isEmpty(rest));
        assertEquals(15, target[2].count());
        assertTrue(OPS.isEmpty(target[0]));
    }

    @Test
    void targetOrderIsFollowedAndExcludedIndicesAreNeverWritten() {
        FakeStack[] target = slots(4);
        // index 0 deliberately left out of the order (e.g. the slot that holds the backpack itself)
        FakeStack rest = move(new FakeStack(1, 10, 64), target, new int[] {1, 2, 3});
        assertTrue(OPS.isEmpty(rest));
        assertTrue(OPS.isEmpty(target[0]));
        assertEquals(10, target[1].count());
    }

    @Test
    void emptySourceChangesNothing() {
        FakeStack[] target = slots(2);
        target[0] = new FakeStack(1, 3, 64);
        FakeStack rest = move(FakeStack.EMPTY, target, order(2));
        assertTrue(OPS.isEmpty(rest));
        assertEquals(3, target[0].count());
    }

    @Test
    void totalIsConservedAndLimitsHoldOnRandomLayouts() {
        Random random = new Random(20261104L);
        for (int round = 0; round < 100; round++) {
            int n = 2 + random.nextInt(12);
            FakeStack[] target = slots(n);
            int[] max = {1, 16, 64};
            for (int i = 0; i < n; i++) {
                if (random.nextInt(3) > 0) {
                    int kind = 1 + random.nextInt(3);
                    int m = max[kind - 1];
                    target[i] = new FakeStack(kind, 1 + random.nextInt(m), m);
                }
            }
            int kind = 1 + random.nextInt(3);
            int m = max[kind - 1];
            FakeStack source = new FakeStack(kind, 1 + random.nextInt(m), m);

            int before = sum(target, kind) + source.count();
            FakeStack rest = move(source, target, order(n));
            int after = sum(target, kind) + rest.count();

            assertEquals(before, after, "round " + round + ": total changed");
            for (FakeStack s : target) {
                assertTrue(s.count() <= s.max(), "round " + round + ": stack over its limit");
            }
        }
    }

    private static int sum(FakeStack[] slots, int kind) {
        int total = 0;
        for (FakeStack s : slots) {
            if (!s.equals(FakeStack.EMPTY) && s.kind() == kind) {
                total += s.count();
            }
        }
        return total;
    }
}
