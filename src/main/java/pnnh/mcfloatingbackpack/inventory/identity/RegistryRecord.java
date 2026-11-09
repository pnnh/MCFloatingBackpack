package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.UUID;

/**
 * 026: what the registry knows of one backpack. {@code owner} is the player who last used it; {@code state} is NORMAL, or
 * DELETED for a tombstone (written by a later feature; this one only refuses to use such a backpack). The contents' shadow copy
 * is kept apart by the registry (it is made of Minecraft types). The registry is an index kept as well as it can be, not a
 * source of truth: the backpack's contents live in the item. Pure logic.
 */
public record RegistryRecord(UUID uuid, UUID owner, int capacity, State state, long createdAt, long touchedAt) {
    public enum State {
        NORMAL,
        DELETED
    }

    public RegistryRecord withOwner(UUID owner) {
        return new RegistryRecord(uuid, owner, capacity, state, createdAt, touchedAt);
    }

    public RegistryRecord withCapacity(int capacity) {
        return new RegistryRecord(uuid, owner, capacity, state, createdAt, touchedAt);
    }

    public RegistryRecord withState(State state) {
        return new RegistryRecord(uuid, owner, capacity, state, createdAt, touchedAt);
    }

    public RegistryRecord withTouchedAt(long touchedAt) {
        return new RegistryRecord(uuid, owner, capacity, state, createdAt, touchedAt);
    }
}
