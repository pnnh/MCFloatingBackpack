package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

/** 021: the trash can's list rules, run over a fake stack type so no Minecraft bootstrap is needed. */
class TrashBinTest {
    record Fake(int kind, int count, int max) {
        static final Fake EMPTY = new Fake(0, 0, 64);
    }

    static final QuickTransferLogic.StackOps<Fake> OPS = new QuickTransferLogic.StackOps<>() {
        @Override
        public Fake empty() {
            return Fake.EMPTY;
        }

        @Override
        public boolean isEmpty(Fake s) {
            return s.count() <= 0;
        }

        @Override
        public int count(Fake s) {
            return s.count();
        }

        @Override
        public int maxStack(Fake s) {
            return s.max();
        }

        @Override
        public boolean sameKind(Fake a, Fake b) {
            return a.kind() == b.kind();
        }

        @Override
        public Fake withCount(Fake s, int count) {
            return count <= 0 ? Fake.EMPTY : new Fake(s.kind(), count, s.max());
        }
    };

    private static Fake f(int kind, int count) {
        return new Fake(kind, count, 64);
    }

    private static List<Fake> list(Fake... stacks) {
        return new ArrayList<>(Arrays.asList(stacks));
    }

    private static int total(List<Fake> groups) {
        return groups.stream().mapToInt(Fake::count).sum();
    }

    @Test
    void pushEmptyStackChangesNothing() {
        TrashBin.Push<Fake> result = TrashBin.push(OPS, list(f(1, 5)), Fake.EMPTY);
        assertEquals(list(f(1, 5)), result.groups());
        assertTrue(OPS.isEmpty(result.destroyed()));
    }

    @Test
    void pushPutsTheNewestFirstAndShiftsTheRest() {
        TrashBin.Push<Fake> result = TrashBin.push(OPS, list(f(1, 5), f(2, 6)), f(3, 7));
        assertEquals(list(f(3, 7), f(1, 5), f(2, 6)), result.groups());
        assertTrue(OPS.isEmpty(result.destroyed()));
    }

    @Test
    void pushNeverMergesSameKind() {
        TrashBin.Push<Fake> result = TrashBin.push(OPS, list(f(1, 5)), f(1, 7));
        assertEquals(list(f(1, 7), f(1, 5)), result.groups());
    }

    @Test
    void pushOnNineGroupsDestroysTheOldest() {
        List<Fake> nine = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            nine.add(f(i, i));
        }
        TrashBin.Push<Fake> result = TrashBin.push(OPS, nine, f(10, 10));
        assertEquals(9, result.groups().size());
        assertEquals(f(10, 10), result.groups().get(0));
        assertEquals(f(1, 1), result.groups().get(1));
        assertEquals(f(8, 8), result.groups().get(8));
        assertEquals(f(9, 9), result.destroyed());
    }

    @Test
    void pushDoesNotChangeTheInputList() {
        List<Fake> input = list(f(1, 5));
        TrashBin.push(OPS, input, f(2, 2));
        assertEquals(list(f(1, 5)), input);
    }

    @Test
    void takeRemovesTheGroupAndMovesTheRestForward() {
        TrashBin.Take<Fake> result = TrashBin.take(OPS, list(f(1, 1), f(2, 2), f(3, 3)), 1);
        assertEquals(f(2, 2), result.taken());
        assertEquals(list(f(1, 1), f(3, 3)), result.groups());
    }

    @Test
    void takeOutOfRangeChangesNothing() {
        List<Fake> groups = list(f(1, 1), f(2, 2));
        for (int index : new int[] {-1, 2, 8, 9, 100}) {
            TrashBin.Take<Fake> result = TrashBin.take(OPS, groups, index);
            assertTrue(OPS.isEmpty(result.taken()), "index " + index);
            assertEquals(groups, result.groups());
        }
    }

    @Test
    void restoreStacksOntoExistingThenFillsEmptySlots() {
        Fake[] main = {f(1, 60), Fake.EMPTY, Fake.EMPTY};
        List<Fake> result = TrashBin.restore(
                OPS, list(f(1, 20)), new int[] {0}, new int[] {0, 1, 2}, i -> main[i], (s, i) -> main[i] = s);
        assertTrue(result.isEmpty());
        assertEquals(f(1, 64), main[0]);
        assertEquals(f(1, 16), main[1]);
        assertTrue(OPS.isEmpty(main[2]));
    }

    @Test
    void restoreKeepsWhatDoesNotFitInPlace() {
        Fake[] main = {f(9, 1), Fake.EMPTY};
        List<Fake> result = TrashBin.restore(
                OPS,
                list(f(1, 10), f(2, 20), f(3, 30)),
                new int[] {0, 1, 2},
                new int[] {0, 1},
                i -> main[i],
                (s, i) -> main[i] = s);
        // only one empty slot: the first group goes in, the other two stay in their order
        assertEquals(list(f(2, 20), f(3, 30)), result);
        assertEquals(f(1, 10), main[1]);
    }

    @Test
    void restorePartialLeavesTheRemainderInTheSamePosition() {
        Fake[] main = {f(1, 60), f(8, 1)};
        List<Fake> result = TrashBin.restore(
                OPS, list(f(5, 5), f(1, 10)), new int[] {1}, new int[] {0, 1}, i -> main[i], (s, i) -> main[i] = s);
        assertEquals(list(f(5, 5), f(1, 6)), result);
        assertEquals(f(1, 64), main[0]);
    }

    @Test
    void restoreIgnoresDuplicatesAndOutOfRangeIndices() {
        Fake[] main = {Fake.EMPTY, Fake.EMPTY};
        List<Fake> result = TrashBin.restore(
                OPS,
                list(f(1, 1), f(2, 2)),
                new int[] {1, 1, -3, 7, 0},
                new int[] {0, 1},
                i -> main[i],
                (s, i) -> main[i] = s);
        assertTrue(result.isEmpty());
        // ascending order: group 0 goes first
        assertEquals(f(1, 1), main[0]);
        assertEquals(f(2, 2), main[1]);
    }

    @Test
    void restoreWithFullMainAreaChangesNothing() {
        Fake[] main = {f(9, 64), f(9, 64)};
        List<Fake> groups = list(f(1, 1), f(2, 2));
        List<Fake> result = TrashBin.restore(
                OPS, groups, new int[] {0, 1}, new int[] {0, 1}, i -> main[i], (s, i) -> main[i] = s);
        assertEquals(groups, result);
        assertEquals(f(9, 64), main[0]);
        assertEquals(f(9, 64), main[1]);
    }

    @Test
    void sanitizeDropsEmptyStacksAndCapsAtNine() {
        List<Fake> raw = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            raw.add(i % 4 == 0 ? Fake.EMPTY : f(i, i));
        }
        List<Fake> clean = TrashBin.sanitize(OPS, raw);
        assertEquals(9, clean.size());
        assertTrue(clean.stream().noneMatch(OPS::isEmpty));
        assertEquals(f(1, 1), clean.get(0));
    }

    @Test
    void sanitizeHandlesNull() {
        assertTrue(TrashBin.sanitize(OPS, null).isEmpty());
    }

    @Test
    void randomOperationsKeepTheInvariants() {
        Random random = new Random(21);
        Fake[] main = new Fake[6];
        Arrays.fill(main, Fake.EMPTY);
        List<Fake> groups = new ArrayList<>();
        int[] targets = {0, 1, 2, 3, 4, 5};
        int pushed = 0;
        int held = 0;
        int destroyed = 0;
        for (int step = 0; step < 200; step++) {
            switch (random.nextInt(3)) {
                case 0 -> {
                    Fake stack = f(1 + random.nextInt(4), 1 + random.nextInt(64));
                    TrashBin.Push<Fake> result = TrashBin.push(OPS, groups, stack);
                    groups = new ArrayList<>(result.groups());
                    pushed += stack.count();
                    destroyed += result.destroyed().count();
                }
                case 1 -> {
                    TrashBin.Take<Fake> result = TrashBin.take(OPS, groups, random.nextInt(11) - 1);
                    groups = new ArrayList<>(result.groups());
                    held += result.taken().count();
                }
                default -> {
                    int n = 1 + random.nextInt(4);
                    int[] indices = new int[n];
                    for (int i = 0; i < n; i++) {
                        indices[i] = random.nextInt(10);
                    }
                    groups = new ArrayList<>(
                            TrashBin.restore(OPS, groups, indices, targets, i -> main[i], (s, i) -> main[i] = s));
                }
            }
            assertTrue(groups.size() <= TrashBin.MAX_GROUPS);
            assertTrue(groups.stream().noneMatch(OPS::isEmpty));
            int owned = total(groups) + Arrays.stream(main).mapToInt(Fake::count).sum() + held + destroyed;
            assertEquals(pushed, owned, "step " + step);
        }
    }
}
