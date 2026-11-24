package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AllAddressingTest {
    @Test
    void membersAreLaidOneAfterTheOther() {
        AllAddressing a = AllAddressing.of(new int[] {27, 54, 108});
        assertEquals(3, a.members());
        assertEquals(189, a.total());
        assertEquals(0, a.start(0));
        assertEquals(27, a.start(1));
        assertEquals(81, a.start(2));
        assertEquals(new AllAddressing.Cell(0, 0), a.locate(0));
        assertEquals(new AllAddressing.Cell(0, 26), a.locate(26));
        assertEquals(new AllAddressing.Cell(1, 0), a.locate(27));
        assertEquals(new AllAddressing.Cell(2, 107), a.locate(188));
    }

    @Test
    void outsideTheGridThereIsNothing() {
        AllAddressing a = AllAddressing.of(new int[] {27, 27});
        assertNull(a.locate(-1));
        assertNull(a.locate(54));
        assertEquals(-1, a.flatOf(0, 27));
        assertEquals(-1, a.flatOf(2, 0));
        assertEquals(-1, a.flatOf(-1, 0));
    }

    @Test
    void locateAndFlatOfAreInverse() {
        int[] sizes = {27, 36, 45, 54, 63, 9, 108, 117, 27, 27, 27, 27, 27, 27, 27, 27};
        AllAddressing a = AllAddressing.of(sizes);
        assertEquals(AllAddressing.MAX_MEMBERS, a.members());
        for (int flat = 0; flat < a.total(); flat++) {
            AllAddressing.Cell cell = a.locate(flat);
            assertEquals(flat, a.flatOf(cell.member(), cell.local()));
        }
    }

    @Test
    void noMembersAndEmptyMembers() {
        assertEquals(0, AllAddressing.of(new int[0]).total());
        assertNull(AllAddressing.of(new int[0]).locate(0));
        AllAddressing a = AllAddressing.of(new int[] {0, 5, 0, 5});
        assertEquals(new AllAddressing.Cell(1, 0), a.locate(0));
        assertEquals(new AllAddressing.Cell(3, 0), a.locate(5));
        assertEquals(new AllAddressing.Cell(3, 4), a.locate(9));
    }
}
