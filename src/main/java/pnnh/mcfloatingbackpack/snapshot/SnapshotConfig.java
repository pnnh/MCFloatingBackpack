package pnnh.mcfloatingbackpack.snapshot;

import net.neoforged.neoforge.common.ModConfigSpec;

import pnnh.mcfloatingbackpack.version.VersionDebug;

/**
 * 057: the {@code [snapshots]} section of the server config: whether snapshots are kept at all and how much disk space they may use.
 * The size is defined with no range on purpose: the config system answers a number outside a range by putting the default back, and
 * what is wanted is the nearest end of 128 - 4096 (and a line in the log); {@link SnapshotSettings} does that. Read when the server
 * starts (the server config is loaded before that).
 */
public final class SnapshotConfig {
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.IntValue maxTotalMb;

    private SnapshotConfig() {
    }

    /** Adds the section to the server config's builder (called once, from {@code DiagConfig}). */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("snapshots");
        enabled = builder.comment(
                        "Whether this mod keeps snapshots of backpacks (copies of what a backpack holds, written outside the world).",
                        "WARNING: with this off no snapshot is written, nothing is protected when a new version of the mod is",
                        "installed, and a mistake, a bug or damaged data can no longer be undone: the snapshots are the only backup of",
                        "the backpacks that is not inside the world. Existing snapshots are not deleted and can still be looked at and restored.",
                        "Read when the server starts; a change needs the world to be reopened.")
                .define("enabled", true);
        maxTotalMb = builder.comment(
                        "The most disk space, in megabytes, all the snapshots of this world may use together. Allowed: 128 - 4096;",
                        "a value outside is changed to the nearest one (and the log says so). When the limit is passed the oldest timed snapshots,",
                        "then the oldest change snapshots, are deleted; the newest snapshot of each backpack and the \"before an update\"",
                        "snapshots are never deleted for this, so the folder can stay above the limit.",
                        "A small limit means older snapshots go sooner. Read when the server starts.")
                .defineInRange("maxTotalMb", SnapshotSettings.DEFAULT_MB, Integer.MIN_VALUE, Integer.MAX_VALUE);
        builder.pop();
    }

    /** What the config and the debug switch say, made safe; the defaults if the config is not loaded (it always is, once the server starts). */
    public static SnapshotSettings settings() {
        boolean on = true;
        int mb = SnapshotSettings.DEFAULT_MB;
        try {
            if (enabled != null) {
                on = enabled.get();
            }
            if (maxTotalMb != null) {
                mb = maxTotalMb.get();
            }
        } catch (IllegalStateException notLoaded) {
            // the defaults
        }
        return SnapshotSettings.from(on, mb, VersionDebug.timedSnapshotSecondsOverride());
    }
}
