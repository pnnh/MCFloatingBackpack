package pnnh.mcfloatingbackpack.worldmarker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 041: the marker's text: what reads as a marker, what is unusable, and that what is written reads back. */
class WorldMarkerTest {
    private static WorldMarker valid(String text) {
        return assertInstanceOf(MarkerRead.Valid.class, WorldMarker.parse(text)).marker();
    }

    private static String corrupt(String text) {
        return assertInstanceOf(MarkerRead.Corrupt.class, WorldMarker.parse(text)).reason();
    }

    @Test
    void whatIsWrittenReadsBack() {
        WorldMarker marker = new WorldMarker(7, "0.2.0-beta.1+build.5");
        assertEquals(marker, valid(marker.toText()));
        WorldMarker withoutVersion = new WorldMarker(3, null);
        assertEquals(withoutVersion, valid(withoutVersion.toText()));
        assertTrue(!withoutVersion.toText().contains("mod_version"));
    }

    @Test
    void aBackslashInTheVersionSurvives() {
        WorldMarker marker = new WorldMarker(1, "a\\b");
        assertEquals(marker, valid(marker.toText()));
    }

    @Test
    void theTextIsAHeaderComment_theRevision_andTheVersion() {
        assertEquals(
                "# MCFloatingBackpack world marker. Delete this file, or lower data_revision, to accept the downgrade risk.\n"
                        + "data_revision=1\nmod_version=0.1.0\n",
                new WorldMarker(1, "0.1.0").toText());
    }

    @Test
    void handEditedTextIsRead() {
        assertEquals(new WorldMarker(5, "0.3.0"), valid("  # a comment\r\n\r\n data_revision = 5 \r\nmod_version=0.3.0\r\n"));
        assertEquals(new WorldMarker(0, null), valid("data_revision=0"));
    }

    @Test
    void aMissingOrBlankVersionIsNoVersion() {
        assertNull(valid("data_revision=2\nmod_version=   \n").modVersion());
        assertNull(valid("data_revision=2").modVersion());
    }

    @Test
    void aRevisionThatIsMissingNotANumberOrNegativeMakesItCorrupt() {
        assertTrue(corrupt("mod_version=0.1.0").contains("missing"));
        assertTrue(corrupt("data_revision=abc").contains("not a whole number"));
        assertTrue(corrupt("data_revision=").contains("not a whole number"));
        assertTrue(corrupt("data_revision=1.5").contains("not a whole number"));
        assertTrue(corrupt("data_revision=99999999999").contains("not a whole number"));
        assertTrue(corrupt("data_revision=-1").contains("negative"));
        assertTrue(corrupt("").contains("missing"));
        assertTrue(corrupt("\u0000\u0001 binary \\u12").length() > 0);
    }

    @Test
    void anUnusableFileIsCorruptEvenWhenItHasAVersion() {
        assertInstanceOf(MarkerRead.Corrupt.class, WorldMarker.parse("data_revision=oops\nmod_version=0.1.0"));
    }

    @Test
    void noContentIsCorrupt() {
        assertInstanceOf(MarkerRead.Corrupt.class, WorldMarker.parse(null));
    }

    @Test
    void aNegativeRevisionCannotBeMade() {
        assertThrows(IllegalArgumentException.class, () -> new WorldMarker(-1, null));
    }

    @Test
    void whatEachReadStandsForAsARevision() {
        assertEquals(0, MarkerRead.MISSING.revision());
        assertEquals(0, new MarkerRead.Corrupt("x").revision());
        assertEquals(4, new MarkerRead.Valid(new WorldMarker(4, null)).revision());
    }
}
