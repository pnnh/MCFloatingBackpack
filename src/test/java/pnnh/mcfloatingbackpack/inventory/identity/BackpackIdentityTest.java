package pnnh.mcfloatingbackpack.inventory.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 026: the identity a stack carries. */
class BackpackIdentityTest {
    private static final UUID U = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void anIdentityWorksItsIdOutOfTheInternalId() {
        BackpackIdentity identity = new BackpackIdentity(U);
        assertEquals(BackpackIds.of(U), identity.id());
        assertTrue(BackpackIds.isValid(identity.id()));
    }

    @Test
    void copiesOfAStackShareTheIdentityAndSoTheId() {
        BackpackIdentity a = new BackpackIdentity(U);
        BackpackIdentity b = new BackpackIdentity(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertEquals(a, b);
        assertEquals(a.id(), b.id());
    }

    @Test
    void differentInternalIdsAreDifferentIdentities() {
        assertNotEquals(new BackpackIdentity(U), new BackpackIdentity(UUID.randomUUID()));
    }

    @Test
    void theInternalIdIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new BackpackIdentity(null));
    }
}
