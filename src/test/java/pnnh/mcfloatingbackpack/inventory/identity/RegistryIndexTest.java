package pnnh.mcfloatingbackpack.inventory.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.identity.RegistryRecord.State;

/** 026: the registry's bookkeeping. Nothing is limited. */
class RegistryIndexTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static RegistryRecord record(UUID uuid, UUID owner) {
        return new RegistryRecord(uuid, owner, 36, State.NORMAL, 1000L, 1000L);
    }

    @Test
    void aRegisteredBackpackCanBeFound() {
        RegistryIndex index = new RegistryIndex();
        UUID id = UUID.randomUUID();
        index.register(record(id, ALICE));
        assertTrue(index.contains(id));
        assertEquals(1, index.size());
        assertEquals(ALICE, index.get(id).owner());
        assertNull(index.get(UUID.randomUUID()));
        assertFalse(index.contains(UUID.randomUUID()));
    }

    @Test
    void anInternalIdCannotBeRegisteredTwice() {
        RegistryIndex index = new RegistryIndex();
        UUID id = UUID.randomUUID();
        index.register(record(id, ALICE));
        assertThrows(IllegalStateException.class, () -> index.register(record(id, BOB)));
        assertEquals(1, index.size());
        assertEquals(ALICE, index.get(id).owner());
    }

    @Test
    void nothingIsLimited() {
        RegistryIndex index = new RegistryIndex();
        for (int i = 0; i < 30000; i++) {
            index.register(record(UUID.randomUUID(), i % 2 == 0 ? ALICE : BOB));
        }
        assertEquals(30000, index.size());
        assertEquals(30000, index.records().size());
    }

    @Test
    void updateChangesTheRecordAndKeepsTheInternalId() {
        RegistryIndex index = new RegistryIndex();
        UUID id = UUID.randomUUID();
        index.register(record(id, ALICE));
        assertTrue(index.update(id, r -> r.withOwner(BOB).withCapacity(54).withTouchedAt(2000L)));
        RegistryRecord now = index.get(id);
        assertEquals(BOB, now.owner());
        assertEquals(54, now.capacity());
        assertEquals(2000L, now.touchedAt());
        assertEquals(1000L, now.createdAt());
        assertEquals(State.NORMAL, now.state());
    }

    @Test
    void updateCannotChangeTheInternalId() {
        RegistryIndex index = new RegistryIndex();
        UUID id = UUID.randomUUID();
        index.register(record(id, ALICE));
        assertThrows(IllegalArgumentException.class, () -> index.update(id, r -> record(UUID.randomUUID(), ALICE)));
        assertEquals(ALICE, index.get(id).owner());
    }

    @Test
    void updatingAnUnknownBackpackSaysSo() {
        assertFalse(new RegistryIndex().update(UUID.randomUUID(), r -> r));
    }

    @Test
    void aTombstoneIsJustAState() {
        RegistryIndex index = new RegistryIndex();
        UUID id = UUID.randomUUID();
        index.register(record(id, ALICE));
        index.update(id, r -> r.withState(State.DELETED));
        assertEquals(State.DELETED, index.get(id).state());
        assertNotNull(index.get(id));
    }

    @Test
    void recordsIsAReadOnlyView() {
        RegistryIndex index = new RegistryIndex();
        index.register(record(UUID.randomUUID(), ALICE));
        assertThrows(UnsupportedOperationException.class, () -> index.records().clear());
    }
}
