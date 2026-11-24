package pnnh.mcfloatingbackpack.client.panel.combined;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 066: the display order of the "all" tab after the sort button: a permutation of all the flat cells, "display position -> flat cell". The
 * cells that hold something come first, in the order of the items (the registry order of 047), and the empty cells after them, each group
 * otherwise in cell order (stable). Nothing is moved or merged: only the picture follows it, the clicks are translated back by it. Pure: what a
 * cell holds is asked of the caller.
 */
public final class AllSortPermutation {
    private AllSortPermutation() {
    }

    /**
     * @param count the number of flat cells
     * @param empty whether cell {@code i} is empty
     * @param byKind the order of two non-empty cells' items (compared by cell number)
     * @return display position -> flat cell; always a permutation of {@code 0..count-1}
     */
    public static int[] of(int count, java.util.function.IntPredicate empty, Comparator<Integer> byKind) {
        Integer[] filled = new Integer[count];
        int[] empties = new int[count];
        int filledCount = 0;
        int emptyCount = 0;
        for (int i = 0; i < count; i++) {
            if (empty.test(i)) {
                empties[emptyCount++] = i;
            } else {
                filled[filledCount++] = i;
            }
        }
        // Arrays.sort on objects is stable: equal kinds stay in cell order
        Arrays.sort(filled, 0, filledCount, byKind);
        int[] order = new int[count];
        for (int i = 0; i < filledCount; i++) {
            order[i] = filled[i];
        }
        System.arraycopy(empties, 0, order, filledCount, emptyCount);
        return order;
    }

    /** The identity order, for the default (unsorted) view. */
    public static int[] identity(int count) {
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        return order;
    }
}
