package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.identity.BackpackIds;
import pnnh.mcfloatingbackpack.snapshot.BackpackSelector.Kind;

/** 030: what a player types to name a backpack. */
class BackpackSelectorTest {
    @Test
    void aFourCharacterIdIsRecognisedWhateverTheCase() {
        assertEquals(Kind.ID, BackpackSelector.parse("K7M2").kind());
        assertEquals(Kind.ID, BackpackSelector.parse("k7m2").kind());
        assertEquals("K7M2", BackpackSelector.parse("k7m2").text());
        assertEquals(Kind.ID, BackpackSelector.parse("  K7M2 ").kind());
    }

    @Test
    void aThreeCharacterIdIsNoLongerAnId() {
        assertEquals(Kind.INVALID, BackpackSelector.parse("K7M").kind());
    }

    @Test
    void aFullUuidIsRecognised() {
        UUID uuid = UUID.randomUUID();
        BackpackSelector.Parsed parsed = BackpackSelector.parse(uuid.toString().toUpperCase());
        assertEquals(Kind.UUID_FULL, parsed.kind());
        assertEquals(uuid.toString(), parsed.text());
    }

    @Test
    void aUuidPrefixOfAtLeastSixCharactersIsRecognised() {
        assertEquals(Kind.UUID_PREFIX, BackpackSelector.parse("123e45").kind());
        assertEquals(Kind.UUID_PREFIX, BackpackSelector.parse("123e4567-e89b").kind());
        assertEquals(Kind.UUID_PREFIX, BackpackSelector.parse("ABCDEF").kind());
    }

    @Test
    void anythingElseIsInvalid() {
        for (String bad : new String[] {"", "  ", "AB", "ABC", "12345", "7ABC", "I000", "zzzzzz", "123e45678901234567890123456789012345678", "not a uuid"}) {
            assertEquals(Kind.INVALID, BackpackSelector.parse(bad).kind(), bad);
        }
        assertEquals(Kind.INVALID, BackpackSelector.parse(null).kind());
    }

    @Test
    void anIdMatchesEveryBackpackWhoseIdItIs() {
        List<UUID> all = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            all.add(UUID.randomUUID());
        }
        UUID one = all.get(7);
        String id = BackpackIds.of(one);
        List<UUID> matches = BackpackSelector.resolve(all, BackpackSelector.parse(id.toLowerCase()));
        assertTrue(matches.contains(one));
        for (UUID m : matches) {
            assertEquals(id, BackpackIds.of(m));
        }
        assertEquals(all.stream().filter(u -> BackpackIds.of(u).equals(id)).count(), matches.size());
    }

    @Test
    void aFullUuidAndAPrefixMatchTheOneBackpack() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        List<UUID> all = List.of(uuid, UUID.randomUUID(), UUID.randomUUID());
        assertEquals(1, BackpackSelector.resolve(all, BackpackSelector.parse(uuid.toString())).size());
        assertEquals(1, BackpackSelector.resolve(all, BackpackSelector.parse("123E45")).size());
        assertEquals(1, BackpackSelector.resolve(all, BackpackSelector.parse("123e4567-e89b-12d3")).size());
        assertEquals(uuid, BackpackSelector.resolve(all, BackpackSelector.parse("123e45")).get(0));
    }

    @Test
    void nothingMatchesAnUnknownOrInvalidSelector() {
        List<UUID> all = List.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertTrue(BackpackSelector.resolve(all, BackpackSelector.parse("ffffffff")).isEmpty());
        assertTrue(BackpackSelector.resolve(all, BackpackSelector.parse("nonsense!")).isEmpty());
        assertTrue(BackpackSelector.resolve(List.of(), BackpackSelector.parse("K7M2")).isEmpty());
    }
}
