package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionRangeTest {
    private static final VersionRange RANGE = VersionRange.of("1.11.0", "1.12.0");

    private static boolean in(String version) {
        return RANGE.contains(ThirdPartyVersion.parse(version).orElseThrow());
    }

    @Test
    void theLowerEndIsInAndTheUpperEndIsOut() {
        assertTrue(in("1.11.0"));
        assertTrue(in("1.11.2"));
        assertTrue(in("1.11.99"));
        assertFalse(in("1.10.99"));
        assertFalse(in("1.12.0"));
        assertFalse(in("1.12.0-beta"));
        assertFalse(in("2.0.0"));
    }

    @Test
    void aShortVersionIsTheSameAsOneWithZeros() {
        assertTrue(in("1.11"));
        assertFalse(in("1.12"));
    }

    @Test
    void toStringIsTheTextTheReadmeHolds() {
        assertEquals("[1.11.0, 1.12.0)", RANGE.toString());
        assertEquals("[2.0.0, 3.0.0)", VersionRange.of("2", "3").toString());
    }

    @Test
    void aRangeMustStartBelowItsEnd() {
        assertThrows(IllegalArgumentException.class, () -> VersionRange.of("1.12.0", "1.11.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionRange.of("1.11.0", "1.11"));
        assertThrows(IllegalArgumentException.class, () -> VersionRange.of("none", "1.0.0"));
    }
}
