package pnnh.mcfloatingbackpack.compat;

import java.util.List;
import java.util.Optional;

/** 058: judges an installed version against a mod's stretches ({@link VersionRange}s, ascending and not overlapping). Pure logic. */
public final class VersionChecker {
    private VersionChecker() {
    }

    public static VersionVerdict check(List<VersionRange> ranges, String installedVersion) {
        Optional<ThirdPartyVersion> parsed = ThirdPartyVersion.parse(installedVersion);
        if (parsed.isEmpty() || ranges.isEmpty()) {
            return VersionVerdict.UNKNOWN;
        }
        ThirdPartyVersion version = parsed.get();
        for (VersionRange range : ranges) {
            if (range.contains(version)) {
                return VersionVerdict.IN_RANGE;
            }
        }
        if (version.compareTo(ranges.get(0).minInclusive()) < 0) {
            return VersionVerdict.BELOW_ALL;
        }
        if (version.compareTo(ranges.get(ranges.size() - 1).maxExclusive()) >= 0) {
            return VersionVerdict.ABOVE_ALL;
        }
        return VersionVerdict.IN_GAP;
    }
}
