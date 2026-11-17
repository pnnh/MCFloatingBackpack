package pnnh.mcfloatingbackpack.snapshot;

/**
 * 057: what the server config and the debug switch say about the snapshots, made safe: whether they are kept at all, the most disk
 * space they may use (the config's number is moved into 128 - 4096 megabytes; {@code clamped} says it had to be), and how often the
 * timed ones are written. Pure logic.
 */
public record SnapshotSettings(boolean enabled, int maxTotalMb, long timedIntervalMs, boolean clamped) {
    public static final int MIN_MB = 128;
    public static final int MAX_MB = 4096;
    public static final int DEFAULT_MB = 768;
    public static final int DEFAULT_INTERVAL_SECONDS = 15 * 60;

    /** @param debugSeconds the debug switch's seconds between timed snapshots, or null (or less than one) for fifteen minutes */
    public static SnapshotSettings from(boolean enabled, int configuredMb, Integer debugSeconds) {
        int mb = Math.max(MIN_MB, Math.min(MAX_MB, configuredMb));
        int seconds = debugSeconds == null || debugSeconds < 1 ? DEFAULT_INTERVAL_SECONDS : debugSeconds;
        return new SnapshotSettings(enabled, mb, seconds * 1000L, mb != configuredMb);
    }

    public long maxTotalBytes() {
        return (long) maxTotalMb * 1024L * 1024L;
    }
}
