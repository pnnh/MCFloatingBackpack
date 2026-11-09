package pnnh.mcfloatingbackpack.snapshot;

import java.util.UUID;

/**
 * 030: what a snapshot file says about itself, apart from the contents: which backpack it is of, who last used that backpack,
 * its capacity, when the snapshot was taken (milliseconds since the epoch) and how many of the main slots held something.
 * Pure data with no Minecraft types.
 *
 * @param owner the player who last used the backpack when the snapshot was taken
 * @param ownerName that player's name then, so a listing needs no profile lookup
 */
public record SnapshotMeta(UUID uuid, UUID owner, String ownerName, int capacity, long time, int slots) {
}
