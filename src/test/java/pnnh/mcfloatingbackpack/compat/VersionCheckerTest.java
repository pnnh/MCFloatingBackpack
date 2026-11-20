package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/** 058: the five answers for one list of stretches, and a whole row with the mods it also needs. */
class VersionCheckerTest {
    private static final List<VersionRange> TWO = List.of(VersionRange.of("1.1.0", "2.0.0"), VersionRange.of("3.2.0", "4.0.0"));

    @Test
    void insideEitherStretch() {
        assertEquals(VersionVerdict.IN_RANGE, VersionChecker.check(TWO, "1.1.0"));
        assertEquals(VersionVerdict.IN_RANGE, VersionChecker.check(TWO, "1.9.9"));
        assertEquals(VersionVerdict.IN_RANGE, VersionChecker.check(TWO, "3.2.0+1.21.1"));
        assertEquals(VersionVerdict.IN_RANGE, VersionChecker.check(TWO, "3.99.0-beta.1"));
    }

    @Test
    void belowEveryStretch() {
        assertEquals(VersionVerdict.BELOW_ALL, VersionChecker.check(TWO, "1.0.9"));
        assertEquals(VersionVerdict.BELOW_ALL, VersionChecker.check(TWO, "0.5"));
    }

    @Test
    void aboveEveryStretch() {
        assertEquals(VersionVerdict.ABOVE_ALL, VersionChecker.check(TWO, "4.0.0"));
        assertEquals(VersionVerdict.ABOVE_ALL, VersionChecker.check(TWO, "4.0.0-beta"));
        assertEquals(VersionVerdict.ABOVE_ALL, VersionChecker.check(TWO, "10.0"));
    }

    @Test
    void betweenTwoStretches() {
        assertEquals(VersionVerdict.IN_GAP, VersionChecker.check(TWO, "2.0.0"));
        assertEquals(VersionVerdict.IN_GAP, VersionChecker.check(TWO, "3.1.9"));
    }

    @Test
    void aTextWithoutANumberCannotBeJudged() {
        assertEquals(VersionVerdict.UNKNOWN, VersionChecker.check(TWO, "snapshot"));
        assertEquals(VersionVerdict.UNKNOWN, VersionChecker.check(TWO, null));
        assertTrue(VersionVerdict.UNKNOWN.outOfRange());
        assertEquals(false, VersionVerdict.IN_RANGE.outOfRange());
    }

    // --- a row with mods it also needs ---------------------------------------------------------------------------

    private static Function<String, Optional<String>> installed(Map<String, String> versions) {
        return id -> Optional.ofNullable(versions.get(id));
    }

    private static final ThirdPartyCompat ROW = ThirdPartyTable.INTEGRATED_TERMINALS_ROW;

    @Test
    void aRowInRangeInEveryMod() {
        CompatFinding found = CompatFinding.check(ROW, installed(Map.of(
                "integratedterminals", "1.11.2", "integrateddynamics", "1.39.1", "commoncapabilities", "2.11.6"))).orElseThrow();
        assertEquals(VersionVerdict.IN_RANGE, found.verdict());
        assertEquals("integratedterminals", found.subjectModId());
    }

    @Test
    void theRowsOwnModOutOfRangeIsTheSubject() {
        CompatFinding found = CompatFinding.check(ROW, installed(Map.of(
                "integratedterminals", "1.13.0", "integrateddynamics", "1.39.1", "commoncapabilities", "2.11.6"))).orElseThrow();
        assertEquals(VersionVerdict.ABOVE_ALL, found.verdict());
        assertEquals("integratedterminals", found.subjectModId());
        assertEquals("1.13.0", found.installedVersion());
        assertEquals(List.of(VersionRange.of("1.11.0", "1.12.0")), found.ranges());
    }

    @Test
    void aRequiredModOutOfRangeIsNamedAsTheSubject() {
        CompatFinding found = CompatFinding.check(ROW, installed(Map.of(
                "integratedterminals", "1.11.2", "integrateddynamics", "1.38.0", "commoncapabilities", "2.11.6"))).orElseThrow();
        assertEquals(VersionVerdict.BELOW_ALL, found.verdict());
        assertEquals("integrateddynamics", found.subjectModId());
        assertEquals("Integrated Dynamics", found.subjectName());
        assertEquals("1.38.0", found.installedVersion());
    }

    @Test
    void theFirstFailingRequirementInDeclarationOrderWins() {
        CompatFinding found = CompatFinding.check(ROW, installed(Map.of(
                "integratedterminals", "1.11.2", "integrateddynamics", "2.0.0", "commoncapabilities", "9.0.0"))).orElseThrow();
        assertEquals("integrateddynamics", found.subjectModId());
    }

    @Test
    void aRowWhoseOwnModIsNotInstalledSaysNothing() {
        assertEquals(Optional.empty(), CompatFinding.check(ROW, installed(Map.of("integrateddynamics", "1.39.1"))));
    }

    @Test
    void aRowWhoseRequiredModIsNotInstalledSaysNothing() {
        assertEquals(Optional.empty(), CompatFinding.check(ROW, installed(Map.of("integratedterminals", "1.11.2", "integrateddynamics", "1.39.1"))));
    }
}
