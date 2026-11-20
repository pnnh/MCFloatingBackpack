package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** 058: the words of the server log's lines. */
class CompatLogTextTest {
    private static CompatFinding finding(String integratedTerminals, String dynamics) {
        return CompatFinding.check(ThirdPartyTable.INTEGRATED_TERMINALS_ROW, id -> Optional.ofNullable(Map.of(
                "integratedterminals", integratedTerminals, "integrateddynamics", dynamics, "commoncapabilities", "2.11.6").get(id))).orElseThrow();
    }

    @Test
    void severalStretchesAreListedAllOfThem() {
        assertEquals("[1.1.0, 2.0.0), [3.2.0, 4.0.0)",
                CompatLogText.ranges(List.of(VersionRange.of("1.1.0", "2.0.0"), VersionRange.of("3.2.0", "4.0.0"))));
    }

    @Test
    void theWarningNamesTheModTheVersionTheVerdictAndTheStretches() {
        String text = CompatLogText.warning(finding("1.13.0", "1.39.1"), false);
        assertTrue(text.contains("Integrated Terminals 1.13.0"), text);
        assertTrue(text.contains("newer than every version"), text);
        assertTrue(text.contains("[1.11.0, 1.12.0)"), text);
        assertTrue(text.contains("switched off"), text);
        assertFalse(text.contains("FORCED"), text);
    }

    @Test
    void theThreeWaysOutOfRangeAreWordedDifferently() {
        String below = CompatLogText.warning(finding("1.10.0", "1.39.1"), false);
        String above = CompatLogText.warning(finding("1.13.0", "1.39.1"), false);
        String unknown = CompatLogText.warning(finding("snapshot", "1.39.1"), false);
        assertTrue(below.contains("older than every version"), below);
        assertTrue(above.contains("newer than every version"), above);
        assertTrue(unknown.contains("cannot be read"), unknown);
        CompatFinding gap = new CompatFinding(ThirdPartyTable.INTEGRATED_TERMINALS_ROW, VersionVerdict.IN_GAP, "x", "X", "2.0.0",
                List.of(VersionRange.of("1.0.0", "2.0.0"), VersionRange.of("3.0.0", "4.0.0")));
        assertTrue(CompatLogText.warning(gap, false).contains("between two of the version ranges"));
    }

    @Test
    void aRequiredModOutOfRangeIsNamedInTheWarning() {
        String text = CompatLogText.warning(finding("1.11.2", "1.38.0"), false);
        assertTrue(text.contains("Integrated Terminals"), text);
        assertTrue(text.contains("Integrated Dynamics 1.38.0"), text);
        assertTrue(text.contains("[1.39.0, 1.40.0)"), text);
    }

    @Test
    void aForcedCompatibilitySaysSoAndWarnsOfTheRisk() {
        String text = CompatLogText.warning(finding("1.13.0", "1.39.1"), true);
        assertTrue(text.contains("FORCED ON"), text);
        assertTrue(text.contains("back up the world"), text);
    }
}
