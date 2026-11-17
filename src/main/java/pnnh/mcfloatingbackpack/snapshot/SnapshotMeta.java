package pnnh.mcfloatingbackpack.snapshot;

import java.util.UUID;

/**
 * 030: what a snapshot file says about itself, apart from the contents: which backpack it is of, who last used that backpack,
 * its capacity, when the snapshot was taken (milliseconds since the epoch) and how many of the main slots held something.
 * 057: and what kind it is; a snapshot of kind {@link SnapshotKind#UPGRADE} also says which mod version wrote it and which one was
 * started when it was pinned. Pure data with no Minecraft types.
 *
 * @param owner the player who last used the backpack when the snapshot was taken
 * @param ownerName that player's name then, so a listing needs no profile lookup
 * @param fromVersion for an upgrade snapshot, the mod version before the change; empty otherwise
 * @param toVersion for an upgrade snapshot, the mod version after the change; empty otherwise
 */
public record SnapshotMeta(
        UUID uuid, UUID owner, String ownerName, int capacity, long time, int slots, SnapshotKind kind, String fromVersion, String toVersion) {
    /** A snapshot of the ordinary kind (what version 1 of the file format always was), with no versions. */
    public static SnapshotMeta of(UUID uuid, UUID owner, String ownerName, int capacity, long time, int slots) {
        return new SnapshotMeta(uuid, owner, ownerName, capacity, time, slots, SnapshotKind.CHANGE, "", "");
    }

    /** The same snapshot of another kind (and the versions that go with it). */
    public SnapshotMeta withKind(SnapshotKind newKind, String from, String to) {
        return new SnapshotMeta(uuid, owner, ownerName, capacity, time, slots, newKind, from, to);
    }
}
