package pnnh.mcfloatingbackpack.compat;

/**
 * 058: one stretch of versions of a third-party mod, {@code [minInclusive, maxExclusive)}. The lower end is in, the upper end is out
 * ({@code [1.11.0, 1.12.0)} holds 1.11.0 and 1.11.99, not 1.12.0 and not 1.12.0-beta). Pure logic.
 */
public record VersionRange(ThirdPartyVersion minInclusive, ThirdPartyVersion maxExclusive) {
    public VersionRange {
        if (minInclusive == null || maxExclusive == null) {
            throw new IllegalArgumentException("a version range needs both ends");
        }
        if (minInclusive.compareTo(maxExclusive) >= 0) {
            throw new IllegalArgumentException("a version range must start below its end: [" + minInclusive + ", " + maxExclusive + ")");
        }
    }

    /** {@code VersionRange.of("1.11.0", "1.12.0")}; the texts must have a number in them. */
    public static VersionRange of(String min, String maxExclusive) {
        return new VersionRange(
                ThirdPartyVersion.parse(min).orElseThrow(() -> new IllegalArgumentException("not a version: " + min)),
                ThirdPartyVersion.parse(maxExclusive).orElseThrow(() -> new IllegalArgumentException("not a version: " + maxExclusive)));
    }

    public boolean contains(ThirdPartyVersion version) {
        return version.compareTo(minInclusive) >= 0 && version.compareTo(maxExclusive) < 0;
    }

    /** The text the README's compatibility table has to hold: {@code [1.11.0, 1.12.0)}. */
    @Override
    public String toString() {
        return "[" + minInclusive + ", " + maxExclusive + ")";
    }
}
