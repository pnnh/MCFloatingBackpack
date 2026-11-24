package pnnh.mcfloatingbackpack.client.panel.combined;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AllGridTest {
    @Test
    void theBackpacksCellsRunOnFromOneAnother() {
        int[] map = AllGrid.displayMap(new int[] {7, 5});
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, map);
    }

    @Test
    void thereIsNothingInBetweenWhateverTheColumnCount() {
        // the layout over the columns is the panel's: the map has no gaps, so the only partly filled row is the last one
        int[] map = AllGrid.displayMap(new int[] {27, 54, 9});
        assertEquals(90, map.length);
        for (int i = 0; i < map.length; i++) {
            assertEquals(i, map[i]);
        }
    }

    @Test
    void noMembersNoCells() {
        assertEquals(0, AllGrid.displayMap(new int[0]).length);
        assertEquals(3, AllGrid.displayMap(new int[] {0, 3, 0}).length);
    }
}
