package pnnh.mcfloatingbackpack.client.panel.combined;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Comparator;

import org.junit.jupiter.api.Test;

class AllSortPermutationTest {
    private static final String[] CELLS = {"", "stone", "apple", "", "stone", "bread"};

    private static int[] sort(String[] cells) {
        Comparator<Integer> byKind = Comparator.comparing(i -> cells[i]);
        return AllSortPermutation.of(cells.length, i -> cells[i].isEmpty(), byKind);
    }

    @Test
    void filledCellsFirstInKindOrderThenTheEmptyOnes() {
        assertArrayEquals(new int[] {2, 5, 1, 4, 0, 3}, sort(CELLS));
    }

    @Test
    void equalKindsKeepTheirCellOrder() {
        int[] order = sort(CELLS);
        // the two stone cells (1 then 4) stay in that order
        int first = indexOf(order, 1);
        int second = indexOf(order, 4);
        assertEquals(true, first < second);
    }

    @Test
    void itIsAlwaysAPermutation() {
        int[] order = sort(CELLS);
        int[] sorted = order.clone();
        Arrays.sort(sorted);
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, sorted);
    }

    @Test
    void sameInputSameOutput() {
        assertArrayEquals(sort(CELLS), sort(CELLS));
    }

    @Test
    void allEmptyIsTheIdentity() {
        String[] none = {"", "", ""};
        assertArrayEquals(new int[] {0, 1, 2}, sort(none));
        assertArrayEquals(new int[] {0, 1, 2}, AllSortPermutation.identity(3));
    }

    @Test
    void searchMapFollowsTheDisplayOrder() {
        int[] order = {2, 5, 1, 4, 0, 3};
        assertArrayEquals(new int[] {5, 4}, AllSearchMap.toFlat(new int[] {1, 3}, order));
        assertArrayEquals(new int[0], AllSearchMap.toFlat(new int[0], order));
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == v) {
                return i;
            }
        }
        return -1;
    }
}
