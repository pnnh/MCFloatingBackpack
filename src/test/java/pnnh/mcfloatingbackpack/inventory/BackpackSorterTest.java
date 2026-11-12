package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic.StackOps;
import pnnh.mcfloatingbackpack.inventory.extension.CategoryMatcher;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;

/** 047: tidying the main area, with plain records standing in for item stacks. */
class BackpackSorterTest {
    /** A stack: what it is (the id, which also says what category takes it: "food:" and "tool:" ids) and how many. Empty = count 0. */
    private record S(String id, int count) {
        static final S EMPTY = new S("", 0);

        @Override
        public String toString() {
            return count == 0 ? "." : id + "x" + count;
        }
    }

    private static final StackOps<S> OPS = new StackOps<>() {
        @Override
        public S empty() {
            return S.EMPTY;
        }

        @Override
        public boolean isEmpty(S stack) {
            return stack.count() <= 0;
        }

        @Override
        public int count(S stack) {
            return stack.count();
        }

        @Override
        public int maxStack(S stack) {
            return stack.id().startsWith("tool:") ? 1 : stack.id().startsWith("small:") ? 16 : 64;
        }

        @Override
        public boolean sameKind(S a, S b) {
            return a.id().equals(b.id());
        }

        @Override
        public S withCount(S stack, int count) {
            return count <= 0 ? S.EMPTY : new S(stack.id(), count);
        }
    };

    private static final CategoryMatcher<S> MATCHER = (category, stack) -> {
        if (stack.count() <= 0) {
            return false;
        }
        if (ExtensionCategories.GENERAL.equals(category)) {
            return true;
        }
        return stack.id().startsWith(category + ":");
    };

    private static final Comparator<S> ORDER = Comparator.comparing(S::id);

    private static List<S> cells(Object... entries) {
        List<S> out = new ArrayList<>();
        for (Object entry : entries) {
            out.add(entry == null ? S.EMPTY : (S) entry);
        }
        return out;
    }

    private static S s(String id, int count) {
        return new S(id, count);
    }

    private static List<S> sort(List<S> slots, int base, String... rows) {
        return BackpackSorter.sort(slots, base, List.of(rows), MATCHER, OPS, ORDER);
    }

    private static String show(List<S> slots) {
        return slots.toString();
    }

    // --- US1: merge, order, pack ------------------------------------------------------------------------------------

    @Test
    void stacksOfOneKindAreMergedToFullStacksAndOneRest() {
        List<S> out = sort(cells(s("cobble", 10), null, s("cobble", 20), s("cobble", 40), null, null), 6);
        assertEquals("[cobblex64, cobblex6, ., ., ., .]", show(out));
    }

    @Test
    void kindsAreInTheOrderGivenAndPackedFromTheFirstCellWithTheFreeCellsAtTheEnd() {
        List<S> out = sort(cells(null, s("zeta", 1), null, s("alpha", 5), s("mid", 2), null, null, null), 8);
        assertEquals("[alphax5, midx2, zetax1, ., ., ., ., .]", show(out));
    }

    @Test
    void aKindWithoutTheSameIdIsNotMerged() {
        // two different kinds of the same item (different components) have different ids here
        List<S> out = sort(cells(s("book:a", 1), s("book:b", 1), s("book:a", 1), null), 4);
        assertEquals("[book:ax2, book:bx1, ., .]", show(out));
    }

    @Test
    void stacksThatCannotStackKeepOneToACell() {
        List<S> out = sort(cells(s("tool:pick", 1), s("tool:axe", 1), s("tool:pick", 1), null), 4);
        assertEquals("[tool:axex1, tool:pickx1, tool:pickx1, .]", show(out));
    }

    @Test
    void aStackOverTheLimitIsSplitAgainWithTheTotalKept() {
        // 100 of a kind that holds 16: 6 full stacks and a rest of 4
        List<S> out = sort(cells(s("small:pearl", 100), null, null, null, null, null, null, null), 8);
        assertEquals("[small:pearlx16, small:pearlx16, small:pearlx16, small:pearlx16, small:pearlx16, small:pearlx16, small:pearlx4, .]", show(out));
    }

    @Test
    void anEmptyBackpackStaysEmpty() {
        List<S> out = sort(cells(null, null, null), 3);
        assertEquals("[., ., .]", show(out));
    }

    @Test
    void tidyingTwiceChangesNothing() {
        List<S> once = sort(cells(s("b", 5), null, s("a", 70), s("b", 60), null, s("c", 1)), 6);
        assertEquals(once, sort(once, 6));
    }

    // --- US2: the category rows first ------------------------------------------------------------------------------

    @Test
    void whatARowTakesGoesIntoThatRowFirstFromItsFirstCell() {
        // 4 main cells and a food row of 9
        List<S> slots = new ArrayList<>(cells(s("food:bread", 10), s("rock", 5), s("food:apple", 3), null));
        for (int i = 0; i < 9; i++) {
            slots.add(S.EMPTY);
        }
        List<S> out = sort(slots, 4, "food");
        assertEquals("[rockx5, ., ., ., food:applex3, food:breadx10, ., ., ., ., ., ., .]", show(out));
    }

    @Test
    void whenTheRowIsFullTheRestOfThatKindGoesToTheMainCellsInOrder() {
        // ten different foods and a rock in a base of 13 cells, a food row of nine: the row takes f0..f8 (the first in order), the
        // tenth food goes to the main cells next to the rock, in order
        List<S> big = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            big.add(s("food:f" + i, 1));
        }
        big.add(s("rock", 1));
        big.add(S.EMPTY);
        big.add(S.EMPTY);
        for (int i = 0; i < 9; i++) {
            big.add(S.EMPTY);
        }
        List<S> out = sort(big, 13, "food");
        assertNotNull(out);
        for (int i = 0; i < 9; i++) {
            assertEquals("food:f" + i, out.get(13 + i).id());
        }
        assertEquals("food:f9", out.get(0).id());
        assertEquals("rock", out.get(1).id());
        assertTrue(OPS.isEmpty(out.get(2)));
    }

    @Test
    void twoRowsOfTheSameCategoryAreFilledOneAfterTheOther() {
        List<S> slots = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            slots.add(s("food:f" + String.format("%02d", i), 1));
        }
        for (int i = 0; i < 18; i++) {
            slots.add(S.EMPTY);
        }
        // a base of 12 cells, then two food rows
        List<S> out = sort(slots, 12, "food", "food");
        assertNotNull(out);
        for (int i = 0; i < 9; i++) {
            assertEquals("food:f" + String.format("%02d", i), out.get(12 + i).id(), "first row cell " + i);
        }
        for (int i = 0; i < 3; i++) {
            assertEquals("food:f" + String.format("%02d", 9 + i), out.get(21 + i).id(), "second row cell " + i);
        }
        assertTrue(out.subList(0, 12).stream().allMatch(OPS::isEmpty), "nothing is left in the main cells");
    }

    @Test
    void aUniversalRowIsOnlyUsedWhenTheMainCellsAreFull() {
        // 3 main cells and a universal row: 3 things fit the main cells, a 4th goes to the universal row
        List<S> slots = new ArrayList<>(cells(s("a", 1), s("b", 1), s("c", 1)));
        for (int i = 0; i < 9; i++) {
            slots.add(S.EMPTY);
        }
        List<S> out = sort(slots, 3, ExtensionCategories.GENERAL);
        assertEquals("[ax1, bx1, cx1, ., ., ., ., ., ., ., ., .]", show(out));

        List<S> four = new ArrayList<>(cells(s("a", 1), s("b", 1), s("c", 1)));
        four.set(2, s("c", 1));
        four.addAll(cells(s("d", 1)));
        for (int i = 0; i < 8; i++) {
            four.add(S.EMPTY);
        }
        // base of 3 but four stacks: the fourth sits in the first cell of the universal row
        List<S> out4 = BackpackSorter.sort(four, 3, List.of(ExtensionCategories.GENERAL), MATCHER, OPS, ORDER);
        assertNotNull(out4);
        assertEquals("d", out4.get(3).id());
    }

    @Test
    void aStackThatFitsTwoRowsGoesToTheFirstRowThatHasRoom() {
        // "food:" things are taken by a food row; here two rows take them: the first is filled before the second is used
        List<S> slots = new ArrayList<>();
        slots.add(s("food:x", 1));
        slots.add(s("food:y", 1));
        for (int i = 0; i < 18; i++) {
            slots.add(S.EMPTY);
        }
        List<S> out = sort(slots, 2, "food", "food");
        assertEquals("food:x", out.get(2).id());
        assertEquals("food:y", out.get(3).id());
        assertTrue(OPS.isEmpty(out.get(11)));
    }

    @Test
    void whatSitsInARowThatDoesNotTakeItAnymoreMovesToTheMainCells() {
        // a tool in the food row (its tag was removed): it goes to the main cells, the food stays in the row
        List<S> slots = new ArrayList<>(cells(null, null, null));
        slots.add(s("tool:old", 1));
        slots.add(s("food:b", 2));
        for (int i = 0; i < 7; i++) {
            slots.add(S.EMPTY);
        }
        List<S> out = sort(slots, 3, "food");
        assertNotNull(out);
        assertEquals("tool:old", out.get(0).id());
        assertEquals("food:b", out.get(3).id());
        assertTrue(OPS.isEmpty(out.get(4)));
    }

    // --- US4: nothing lost, or nothing changed ---------------------------------------------------------------------

    @Test
    void whenThereIsNoPlaceTheWholeTidyingIsGivenUp() {
        // a base of 2 full of other things, a food row holding a tool that no row takes: nowhere for it to go
        List<S> slots = new ArrayList<>(cells(s("a", 1), s("b", 1)));
        slots.add(s("tool:old", 1));
        for (int i = 0; i < 8; i++) {
            slots.add(S.EMPTY);
        }
        assertNull(sort(slots, 2, "food"));
    }

    @Test
    void theInputIsNeverChanged() {
        List<S> slots = cells(s("b", 5), null, s("a", 70), s("b", 60));
        List<S> copy = new ArrayList<>(slots);
        sort(slots, 4);
        assertEquals(copy, slots);
    }

    @Test
    void randomBackpacksKeepEveryTotalAndEveryRule() {
        Random random = new Random(47);
        String[] ids = {"a", "b", "c", "small:p", "tool:t", "food:x", "food:y", "tool:u", "big", "gem"};
        String[][] rowSets = {{}, {"food"}, {"tool"}, {"food", "tool"}, {"food", "food"}, {ExtensionCategories.GENERAL}, {"food", ExtensionCategories.GENERAL}};
        for (int round = 0; round < 1000; round++) {
            int base = 9 + random.nextInt(40);
            String[] rows = rowSets[random.nextInt(rowSets.length)];
            int size = base + rows.length * 9;
            List<S> slots = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                if (random.nextInt(3) == 0) {
                    slots.add(S.EMPTY);
                    continue;
                }
                String id = ids[random.nextInt(ids.length)];
                int max = OPS.maxStack(s(id, 1));
                slots.add(s(id, 1 + random.nextInt(max)));
            }
            Map<String, Integer> before = totals(slots);
            List<S> out = BackpackSorter.sort(slots, base, List.of(rows), MATCHER, OPS, ORDER);
            if (out == null) {
                continue; // given up: allowed, and then nothing may be written (checked by theInputIsNeverChanged)
            }
            assertEquals(size, out.size(), "round " + round);
            assertEquals(before, totals(out), "round " + round + " totals");
            for (int cell = 0; cell < size; cell++) {
                S stack = out.get(cell);
                if (OPS.isEmpty(stack)) {
                    continue;
                }
                assertTrue(stack.count() <= OPS.maxStack(stack), "round " + round + " over the limit at " + cell);
                if (cell >= base) {
                    assertTrue(MATCHER.matches(rows[(cell - base) / 9], stack), "round " + round + " row cell " + cell + " " + stack);
                }
            }
            assertEquals(out, BackpackSorter.sort(out, base, List.of(rows), MATCHER, OPS, ORDER), "round " + round + " idempotent");
        }
    }

    private static Map<String, Integer> totals(List<S> slots) {
        Map<String, Integer> totals = new HashMap<>();
        for (S stack : slots) {
            if (!OPS.isEmpty(stack)) {
                totals.merge(stack.id(), stack.count(), Integer::sum);
            }
        }
        return totals;
    }

    @Test
    void mostRandomRoundsAreNotGivenUp() {
        // the 1000 rounds above skip the ones that are given up; make sure that is the exception, not the rule
        Random random = new Random(47);
        int done = 0;
        for (int round = 0; round < 200; round++) {
            int base = 20;
            List<S> slots = new ArrayList<>();
            for (int i = 0; i < base; i++) {
                slots.add(random.nextBoolean() ? S.EMPTY : s("a", 1 + random.nextInt(64)));
            }
            if (BackpackSorter.sort(slots, base, List.of(), MATCHER, OPS, ORDER) != null) {
                done++;
            }
        }
        assertEquals(200, done);
        assertFalse(done == 0);
    }

    @Test
    void aFullBackpackOfOneHundredAndEightCellsAndTwoRowsIsTidiedInAFewMilliseconds() {
        // SC-005: the pure part of what the server does on one click (the real cost adds item stack work on top of it)
        Random random = new Random(5);
        String[] rows = {"food", "tool"};
        int base = 108;
        List<S> slots = new ArrayList<>();
        for (int i = 0; i < base + rows.length * 9; i++) {
            String id = (random.nextBoolean() ? "food:" : random.nextBoolean() ? "tool:" : "k:") + random.nextInt(60);
            slots.add(random.nextInt(5) == 0 ? S.EMPTY : s(id, 1 + random.nextInt(OPS.maxStack(s(id, 1)))));
        }
        for (int warm = 0; warm < 200; warm++) {
            BackpackSorter.sort(slots, base, List.of(rows), MATCHER, OPS, ORDER);
        }
        long start = System.nanoTime();
        int rounds = 100;
        for (int round = 0; round < rounds; round++) {
            BackpackSorter.sort(slots, base, List.of(rows), MATCHER, OPS, ORDER);
        }
        long micros = (System.nanoTime() - start) / rounds / 1000;
        System.out.println("SC-005 cost: tidying 126 cells with 2 rows: " + micros + " us per tidy");
        assertTrue(micros < 50_000, "under 50 ms even on a slow machine, was " + micros + " us");
    }
}
