package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** 058: reading the numbers at the front of a version text. The texts are the real ones from the mods this repository builds with. */
class ThirdPartyVersionTest {
    private static String parsed(String text) {
        return ThirdPartyVersion.parse(text).map(ThirdPartyVersion::toString).orElse("none");
    }

    @Test
    void realVersionTexts() {
        assertEquals("1.11.2", parsed("1.11.2"));
        assertEquals("1.39.1", parsed("1.39.1"));
        assertEquals("2.11.6", parsed("2.11.6"));
        assertEquals("1.30.2", parsed("1.30.2"));
        assertEquals("21.1.22", parsed("21.1.22"));
    }

    @Test
    void buildMetadataAndPreReleaseTagsAreIgnored() {
        assertEquals("9.5.1", parsed("9.5.1+1.21.1"));
        assertEquals("1.1.0", parsed("1.1.0-beta.54+1.21.1"));
        assertEquals("1.12.0", parsed("1.12.0-beta"));
        assertEquals("2.0.0", parsed("2.0.0-SNAPSHOT"));
    }

    @Test
    void aLeadingLetterIsSkippedAndTwoDotsStopTheNumbers() {
        assertEquals("2.0.0", parsed("v2.0"));
        assertEquals("1.0.0", parsed("1..2"));
        assertEquals("3.4.0", parsed("3.4."));
    }

    @Test
    void textWithoutANumberCannotBeJudged() {
        assertEquals(Optional.empty(), ThirdPartyVersion.parse("snapshot"));
        assertEquals(Optional.empty(), ThirdPartyVersion.parse(""));
        assertEquals(Optional.empty(), ThirdPartyVersion.parse("   "));
        assertEquals(Optional.empty(), ThirdPartyVersion.parse(null));
    }

    @Test
    void aNumberTooBigForALongCannotBeJudgedAndDoesNotThrow() {
        assertEquals(Optional.empty(), ThirdPartyVersion.parse("99999999999999999999999.1"));
    }

    @Test
    void missingTrailingPartsCountAsZero() {
        ThirdPartyVersion a = ThirdPartyVersion.parse("1.11").orElseThrow();
        ThirdPartyVersion b = ThirdPartyVersion.parse("1.11.0").orElseThrow();
        assertEquals(0, a.compareTo(b));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(ThirdPartyVersion.parse("1.11.0.1").orElseThrow().compareTo(b) > 0);
    }

    @Test
    void numbersAreComparedAsNumbersNotText() {
        assertTrue(ThirdPartyVersion.parse("1.10.0").orElseThrow().compareTo(ThirdPartyVersion.parse("1.9.9").orElseThrow()) > 0);
        assertTrue(ThirdPartyVersion.parse("0.9.5").orElseThrow().compareTo(ThirdPartyVersion.parse("0.10.0").orElseThrow()) < 0);
        assertFalse(ThirdPartyVersion.parse("1.2.3").orElseThrow().equals(ThirdPartyVersion.parse("1.2.4").orElseThrow()));
    }
}
