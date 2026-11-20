package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ThirdPartyTableTest {
    private static ThirdPartyCompat row(String id, List<VersionRange> ranges, String variant) {
        return new ThirdPartyCompat(id, id, ranges, List.of(), variant);
    }

    private static VersionRange r(String a, String b) {
        return VersionRange.of(a, b);
    }

    @Test
    void theRealTableIsSound() {
        assertEquals(List.of(), ThirdPartyTable.validate(ThirdPartyTable.ALL));
    }

    @Test
    void integratedTerminalsRowHoldsTheVerifiedStretches() {
        ThirdPartyCompat row = ThirdPartyTable.INTEGRATED_TERMINALS_ROW;
        assertEquals("integratedterminals", row.modId());
        assertEquals(List.of(r("1.11.0", "1.12.0")), row.supported());
        assertEquals(List.of("integrateddynamics", "commoncapabilities"), row.also().stream().map(Requirement::modId).toList());
    }

    @Test
    void severalSeparateStretchesAreSound() {
        assertEquals(List.of(), ThirdPartyTable.validate(List.of(row("a", List.of(r("1.1.0", "2.0.0"), r("3.2.0", "4.0.0")), null))));
        assertEquals(List.of(), ThirdPartyTable.validate(List.of(row("a", List.of(r("1.0.0", "2.0.0"), r("2.0.0", "3.0.0")), null))));
    }

    @Test
    void overlappingStretchesAreReported() {
        List<String> problems = ThirdPartyTable.validate(List.of(row("a", List.of(r("1.0.0", "2.5.0"), r("2.0.0", "3.0.0")), null)));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("overlap"), problems.get(0));
    }

    @Test
    void stretchesOutOfOrderAreReported() {
        assertEquals(1, ThirdPartyTable.validate(List.of(row("a", List.of(r("3.0.0", "4.0.0"), r("1.0.0", "2.0.0")), null))).size());
    }

    @Test
    void noStretchesIsReported() {
        assertEquals(1, ThirdPartyTable.validate(List.of(row("a", List.of(), null))).size());
    }

    @Test
    void aModListedTwiceIsReported() {
        List<String> problems = ThirdPartyTable.validate(List.of(row("a", List.of(r("1", "2")), null), row("a", List.of(r("3", "4")), null)));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("more than once"), problems.get(0));
    }

    @Test
    void aVariantIsReportedWhileItIsOnlyReserved() {
        assertEquals(1, ThirdPartyTable.validate(List.of(row("a", List.of(r("1", "2")), "new-api"))).size());
    }

    @Test
    void aRequirementIsCheckedLikeTheRowItself() {
        ThirdPartyCompat bad = new ThirdPartyCompat("a", "a", List.of(r("1", "2")),
                List.of(new Requirement("b", "b", List.of(r("1", "3"), r("2", "4")))), null);
        assertEquals(1, ThirdPartyTable.validate(List.of(bad)).size());
    }
}
