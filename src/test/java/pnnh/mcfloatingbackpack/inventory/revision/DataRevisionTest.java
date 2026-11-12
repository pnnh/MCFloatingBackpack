package pnnh.mcfloatingbackpack.inventory.revision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataRevisionTest {
    @Test
    void theCurrentRevisionIsThreeSince051AddedTheNineCategories() {
        assertEquals(3, DataRevision.CURRENT);
    }

    @Test
    void onlyAHigherRevisionIsNewer() {
        assertFalse(DataRevision.isNewer(0));
        assertFalse(DataRevision.isNewer(1));
        assertFalse(DataRevision.isNewer(2));
        assertFalse(DataRevision.isNewer(3));
        assertTrue(DataRevision.isNewer(4));
        assertTrue(DataRevision.isNewer(99));
    }

    @Test
    void aMissingOrNegativeStampReadsAsZero() {
        assertEquals(0, DataRevision.readStored(null));
        assertEquals(0, DataRevision.readStored(-5));
        assertEquals(0, DataRevision.readStored(0));
        assertEquals(7, DataRevision.readStored(7));
        assertFalse(DataRevision.isNewer(DataRevision.readStored(null)));
        assertFalse(DataRevision.isNewer(DataRevision.readStored(-1)));
    }
}
