package pnnh.mcfloatingbackpack.compat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 058: the one place that says which versions of which third-party mods this mod's active compatibility code was verified with. It is
 * code, not the {@code neoforge.mods.toml}, on purpose: FML refuses to load the game when an installed optional dependency is outside its
 * {@code versionRange}, so the loader must be told {@code [0,)} and the real ranges are kept here, where a version outside them only
 * turns that compatibility off and warns the server's operators ({@link CompatRuntimeState}). A test keeps the README's table, the
 * mods.toml and the development dependencies in build.gradle in line with this table.
 *
 * <p>When a newer version has been tried, add a stretch to its row; the old stretches stay. Pure logic.
 */
public final class ThirdPartyTable {
    public static final String INTEGRATED_TERMINALS = "integratedterminals";

    /** Integrated Terminals 1.11.x, with the Integrated Dynamics 1.39.x and Common Capabilities 2.11.x it was tried with (1.11.2, 1.39.1, 2.11.6). */
    public static final ThirdPartyCompat INTEGRATED_TERMINALS_ROW = new ThirdPartyCompat(
            INTEGRATED_TERMINALS,
            "Integrated Terminals",
            List.of(VersionRange.of("1.11.0", "1.12.0")),
            List.of(
                    new Requirement("integrateddynamics", "Integrated Dynamics", List.of(VersionRange.of("1.39.0", "1.40.0"))),
                    new Requirement("commoncapabilities", "Common Capabilities", List.of(VersionRange.of("2.11.0", "2.12.0")))),
            null);

    public static final List<ThirdPartyCompat> ALL = List.of(INTEGRATED_TERMINALS_ROW);

    private ThirdPartyTable() {
    }

    /** What is wrong with a table: empty when it is sound. */
    public static List<String> validate(List<ThirdPartyCompat> table) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ThirdPartyCompat entry : table) {
            if (entry.modId() == null || entry.modId().isBlank()) {
                problems.add("a row has no mod id");
                continue;
            }
            if (!seen.add(entry.modId())) {
                problems.add(entry.modId() + ": listed more than once");
            }
            if (entry.variant() != null) {
                problems.add(entry.modId() + ": variant is reserved and must be null for now");
            }
            checkRanges(entry.modId(), entry.supported(), problems);
            for (Requirement required : entry.also()) {
                checkRanges(entry.modId() + " also " + required.modId(), required.supported(), problems);
            }
        }
        return problems;
    }

    private static void checkRanges(String owner, List<VersionRange> ranges, List<String> problems) {
        if (ranges.isEmpty()) {
            problems.add(owner + ": no supported version range");
            return;
        }
        for (int i = 1; i < ranges.size(); i++) {
            if (ranges.get(i).minInclusive().compareTo(ranges.get(i - 1).maxExclusive()) < 0) {
                problems.add(owner + ": " + ranges.get(i - 1) + " and " + ranges.get(i) + " overlap or are not in ascending order");
            }
        }
    }
}
