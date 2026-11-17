package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 057: which of two mod versions is the older, only to choose the words of a notice. */
class VersionOrderTest {
    @Test
    void theNumbersAreComparedLeftToRight() {
        assertTrue(VersionOrder.compare("0.1.0", "0.2.0") < 0);
        assertTrue(VersionOrder.compare("0.10.0", "0.9.5") > 0);
        assertTrue(VersionOrder.compare("1.0", "0.99.99") > 0);
        assertEquals(0, VersionOrder.compare("1.2.3", "1.2.3"));
    }

    @Test
    void aMissingPartCountsAsZeroAndTextAroundTheNumbersIsIgnored() {
        assertEquals(0, VersionOrder.compare("1.2", "1.2.0"));
        assertTrue(VersionOrder.compare("1.2", "1.2.1") < 0);
        assertTrue(VersionOrder.compare("v0.1.0-beta", "0.1.1+build5") < 0);
    }

    @Test
    void aVersionWithNoNumberCannotBeCompared() {
        assertNull(VersionOrder.compare("unknown", "1.0"));
        assertNull(VersionOrder.compare("1.0", ""));
        assertNull(VersionOrder.compare(null, "1.0"));
        assertNull(VersionOrder.compare("1.0", null));
    }

    @Test
    void aVeryLongNumberDoesNotBreakIt() {
        assertTrue(VersionOrder.compare("99999999999999999999999.1", "1.1") > 0);
    }
}
