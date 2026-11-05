package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.CompatPolicy.Parsed;
import pnnh.mcfloatingbackpack.compat.CompatPolicy.Problem;

class CompatPolicyTest {
    private static Parsed parse(String... entries) {
        return CompatPolicy.parse(Arrays.asList(entries));
    }

    @Test
    void levelParsingIsCaseInsensitiveAndTrimsSpaces() {
        assertEquals(CompatLevel.FULL, CompatLevel.parse("FULL"));
        assertEquals(CompatLevel.FULL, CompatLevel.parse("full"));
        assertEquals(CompatLevel.OFF, CompatLevel.parse(" Off "));
        assertEquals(CompatLevel.GENERIC, CompatLevel.parse("GENERIC"));
        assertNull(CompatLevel.parse("HALF"));
        assertNull(CompatLevel.parse(""));
        assertNull(CompatLevel.parse(null));
    }

    @Test
    void anEmptyOrMissingListMeansEverythingIsFullWithoutWarnings() {
        for (Parsed parsed : List.of(CompatPolicy.parse(List.of()), CompatPolicy.parse(null))) {
            assertEquals(CompatLevel.FULL, parsed.policy().levelOf("sophisticatedbackpacks"));
            assertTrue(parsed.warnings().isEmpty());
        }
    }

    @Test
    void validEntriesTakeEffectWhateverTheCase() {
        for (String level : new String[] {"off", "Off", "OFF"}) {
            Parsed parsed = parse("sophisticatedbackpacks=" + level);
            assertEquals(CompatLevel.OFF, parsed.policy().levelOf("sophisticatedbackpacks"), level);
            assertTrue(parsed.warnings().isEmpty());
        }
        Parsed spaced = parse("  travelersbackpack = GENERIC ");
        assertEquals(CompatLevel.GENERIC, spaced.policy().levelOf("travelersbackpack"));
    }

    @Test
    void anUnknownLevelFallsBackToFullWithAWarning() {
        Parsed parsed = parse("sophisticatedbackpacks=HALF");
        assertEquals(CompatLevel.FULL, parsed.policy().levelOf("sophisticatedbackpacks"));
        assertEquals(1, parsed.warnings().size());
    }

    @Test
    void invalidIdsAreIgnoredWithAWarning() {
        for (String bad : new String[] {"Bad Id=OFF", "=OFF", "abc", "UPPER=OFF", "has-dash=OFF", "a.b=OFF"}) {
            Parsed parsed = parse(bad);
            assertEquals(1, parsed.warnings().size(), bad);
            assertEquals(CompatLevel.FULL, parsed.policy().levelOf("abc"), bad);
        }
    }

    @Test
    void vanillaCannotBeConfigured() {
        Parsed parsed = parse("minecraft=OFF");
        assertEquals(1, parsed.warnings().size());
        assertEquals(CompatLevel.FULL, parsed.policy().levelOf("minecraft"));
        assertTrue(parsed.policy().allowsInteraction("minecraft"));
    }

    @Test
    void aDuplicateTakesTheLastEntryWithAWarning() {
        Parsed parsed = parse("jei=OFF", "jei=GENERIC");
        assertEquals(CompatLevel.GENERIC, parsed.policy().levelOf("jei"));
        assertEquals(1, parsed.warnings().size());
    }

    @Test
    void oneBadEntryDoesNotSpoilTheOthers() {
        Parsed parsed = parse("sophisticatedbackpacks=OFF", "Bad Id=OFF", "travelersbackpack=GENERIC");
        assertEquals(CompatLevel.OFF, parsed.policy().levelOf("sophisticatedbackpacks"));
        assertEquals(CompatLevel.GENERIC, parsed.policy().levelOf("travelersbackpack"));
        assertEquals(1, parsed.warnings().size());
    }

    @Test
    void nullAndBlankEntriesAreIgnored() {
        Parsed parsed = parse(null, "", "   ", "jei=OFF");
        assertEquals(CompatLevel.OFF, parsed.policy().levelOf("jei"));
    }

    @Test
    void onlyOffRefusesInteraction() {
        Parsed parsed = parse("a=OFF", "b=GENERIC", "c=FULL");
        assertFalse(parsed.policy().allowsInteraction("a"));
        assertTrue(parsed.policy().allowsInteraction("b"));
        assertTrue(parsed.policy().allowsInteraction("c"));
        assertTrue(parsed.policy().allowsInteraction("unlisted"));
        assertTrue(parsed.policy().allowsInteraction(null));
        assertTrue(parsed.policy().allowsInteraction(""));
    }

    @Test
    void onlyFullKeepsTheSpecificAdaptation() {
        Parsed parsed = parse("jei=GENERIC", "x=OFF", "y=FULL");
        assertFalse(parsed.policy().specificAdaptation("jei"));
        assertFalse(parsed.policy().specificAdaptation("x"));
        assertTrue(parsed.policy().specificAdaptation("y"));
        assertTrue(parsed.policy().specificAdaptation("unlisted"));
        assertFalse(parse("jei=OFF").policy().specificAdaptation("jei"));
    }

    @Test
    void genericDoesNotHideThePanelAndTheDefaultShowsIt() {
        assertTrue(parse("sophisticatedbackpacks=GENERIC").policy().allowsInteraction("sophisticatedbackpacks"));
        assertTrue(CompatPolicy.DEFAULT.allowsInteraction("sophisticatedbackpacks"));
        assertFalse(parse("sophisticatedbackpacks=GENERIC").policy().specificAdaptation("sophisticatedbackpacks"));
    }

    @Test
    void warningsSayWhatWasWrongAndAboutWhichEntry() {
        Parsed parsed = parse("a b=OFF", "minecraft=OFF", "x=HALF", "jei=OFF", "jei=GENERIC");
        List<Problem> problems = parsed.warnings().stream().map(CompatPolicy.Warning::problem).toList();
        assertEquals(
                List.of(Problem.MALFORMED, Problem.VANILLA, Problem.UNKNOWN_LEVEL, Problem.DUPLICATE), problems);
        assertEquals("a b=OFF", parsed.warnings().get(0).entry());
        assertTrue(parsed.warnings().get(2).describe().contains("x=HALF"));
        assertEquals("jei", parsed.warnings().get(3).entry());
    }
}
