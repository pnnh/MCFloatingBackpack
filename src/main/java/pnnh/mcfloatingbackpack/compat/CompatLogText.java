package pnnh.mcfloatingbackpack.compat;

import java.util.List;
import java.util.stream.Collectors;

/** 058: the English text of the server log's lines about third-party versions. Pure logic. */
public final class CompatLogText {
    private CompatLogText() {
    }

    /** {@code [1.11.0, 1.12.0)} or, for several stretches, {@code [1.1.0, 2.0.0), [3.2.0, 4.0.0)}. */
    public static String ranges(List<VersionRange> ranges) {
        return ranges.stream().map(VersionRange::toString).collect(Collectors.joining(", "));
    }

    private static String verdictWords(VersionVerdict verdict) {
        return switch (verdict) {
            case BELOW_ALL -> "is older than every version this mod was verified with";
            case ABOVE_ALL -> "is newer than every version this mod was verified with";
            case IN_GAP -> "lies between two of the version ranges this mod was verified with";
            case UNKNOWN -> "has a version number that cannot be read";
            case IN_RANGE -> "is inside the verified versions";
        };
    }

    /** The WARN line for a row outside the verified versions; {@code forced} when the server config keeps its compatibility on anyway. */
    public static String warning(CompatFinding finding, boolean forced) {
        return "Third-party mod " + finding.entry().displayName() + ": the installed " + finding.subjectName() + " " + finding.installedVersion()
                + " " + verdictWords(finding.verdict()) + " (" + ranges(finding.ranges()) + "); active compatibility is "
                + (forced
                        ? "FORCED ON by allowUntestedVersions in the server config, at your own risk (back up the world; set it back to false if anything goes wrong)"
                        : "switched off (only the generic path of the panel remains)")
                + ".";
    }

    /** The ERROR line for a compatibility that failed and was switched off. */
    public static String failure(String displayName) {
        return "Third-party mod " + displayName + ": its active compatibility code failed and is switched off until the server restarts"
                + " (the stack was left where it was and the generic path took over)";
    }
}
