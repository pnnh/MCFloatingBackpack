package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SlotAccessTest {
    private static final class Cells implements SlotAccess<String> {
        final String[] cells;
        final int firstRestricted;

        Cells(int size, int firstRestricted) {
            this.cells = new String[size];
            java.util.Arrays.fill(cells, "");
            this.firstRestricted = firstRestricted;
        }

        @Override
        public int size() {
            return cells.length;
        }

        @Override
        public String get(int index) {
            return cells[index];
        }

        @Override
        public void set(int index, String stack) {
            cells[index] = stack;
        }

        @Override
        public boolean canPlace(int index, String stack) {
            return index < firstRestricted;
        }
    }

    @Test
    void theFlatGridReadsAndWritesTheMembersCells() {
        Cells a = new Cells(3, 3);
        Cells b = new Cells(2, 1);
        AllSlotAccess<String> all = new AllSlotAccess<>(AllAddressing.of(new int[] {3, 2}), List.of(a, b));
        assertEquals(5, all.size());
        all.set(1, "x");
        all.set(4, "y");
        assertEquals("x", a.cells[1]);
        assertEquals("y", b.cells[1]);
        assertEquals("y", all.get(4));
        assertTrue(all.touched(0));
        assertTrue(all.touched(1));
    }

    @Test
    void canPlaceIsTheMembersOwn() {
        Cells a = new Cells(3, 3);
        Cells b = new Cells(2, 1);
        AllSlotAccess<String> all = new AllSlotAccess<>(AllAddressing.of(new int[] {3, 2}), List.of(a, b));
        assertTrue(all.canPlace(2, "s"));
        assertTrue(all.canPlace(3, "s"));
        assertFalse(all.canPlace(4, "s"));
    }

    @Test
    void readingTouchesNothing() {
        Cells a = new Cells(3, 3);
        Cells b = new Cells(2, 1);
        AllSlotAccess<String> all = new AllSlotAccess<>(AllAddressing.of(new int[] {3, 2}), List.of(a, b));
        all.get(0);
        all.canPlace(4, "s");
        assertFalse(all.touched(0));
        assertFalse(all.touched(1));
    }
}
