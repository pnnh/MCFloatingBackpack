package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 026: the registry's bookkeeping, without anything of Minecraft: the records by internal id. Nothing is limited: not how
 * many backpacks there are, not how many one player has. Only the server's main thread touches it.
 */
public final class RegistryIndex {
    private final Map<UUID, RegistryRecord> byUuid = new HashMap<>();

    /** @throws IllegalStateException if the internal id is already registered */
    public void register(RegistryRecord record) {
        if (byUuid.putIfAbsent(record.uuid(), record) != null) {
            throw new IllegalStateException("backpack already registered: " + record.uuid());
        }
    }

    /**
     * Replaces the record with what {@code change} makes of it, which must keep the internal id.
     *
     * @return false if there is no such backpack
     */
    public boolean update(UUID uuid, UnaryOperator<RegistryRecord> change) {
        RegistryRecord record = byUuid.get(uuid);
        if (record == null) {
            return false;
        }
        RegistryRecord next = change.apply(record);
        if (!next.uuid().equals(record.uuid())) {
            throw new IllegalArgumentException("update must not change the internal id");
        }
        byUuid.put(uuid, next);
        return true;
    }

    public RegistryRecord get(UUID uuid) {
        return byUuid.get(uuid);
    }

    public boolean contains(UUID uuid) {
        return byUuid.containsKey(uuid);
    }

    public int size() {
        return byUuid.size();
    }

    /** Every record (a read-only view). */
    public Collection<RegistryRecord> records() {
        return Collections.unmodifiableCollection(byUuid.values());
    }
}
