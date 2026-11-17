package pnnh.mcfloatingbackpack.snapshot.net;

/**
 * 031: one snapshot of a backpack, in a version list (newest first). 057: and its kind ({@link pnnh.mcfloatingbackpack.snapshot.SnapshotKind#code()}),
 * and for one from before an update the mod version it was pinned for (empty otherwise). Pure data.
 */
public record VersionRow(long time, int slots, String ownerName, int capacity, byte kind, String toVersion) {
}
