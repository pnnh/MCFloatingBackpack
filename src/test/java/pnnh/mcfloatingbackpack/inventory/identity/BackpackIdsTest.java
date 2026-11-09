package pnnh.mcfloatingbackpack.inventory.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 026: the four-character backpack ids: format, count, and working an id out of an internal id. */
class BackpackIdsTest {
    @Test
    void thereAre786432Ids() {
        assertEquals(786432, BackpackIds.TOTAL);
        assertEquals(24 * 32 * 32 * 32, BackpackIds.TOTAL);
        assertEquals(4, BackpackIds.LENGTH);
    }

    @Test
    void validIds() {
        assertTrue(BackpackIds.isValid("K7M2"));
        assertTrue(BackpackIds.isValid("A222"));
        assertTrue(BackpackIds.isValid("ZZZZ"));
        assertTrue(BackpackIds.isValid("B999"));
        assertTrue(BackpackIds.isValid("HJKL"));
    }

    @Test
    void invalidIds() {
        assertFalse(BackpackIds.isValid(null));
        assertFalse(BackpackIds.isValid(""));
        assertFalse(BackpackIds.isValid("AB"));
        assertFalse(BackpackIds.isValid("K7M"), "three characters were the old length");
        assertFalse(BackpackIds.isValid("ABCDE"));
        assertFalse(BackpackIds.isValid("7ABC"), "no digit first");
        assertFalse(BackpackIds.isValid("k7m2"), "capital letters only");
        // the confusable characters are left out
        for (String bad : new String[] {"IABC", "OABC", "AIBC", "AOBC", "ABIC", "ABOC", "ABCI", "ABCO", "A0BC", "A1BC", "AB0C", "AB1C", "ABC0", "ABC1"}) {
            assertFalse(BackpackIds.isValid(bad), bad);
        }
    }

    @Test
    void everyIndexRoundTripsAndNoTwoIdsAreEqual() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < BackpackIds.TOTAL; i++) {
            String id = BackpackIds.fromIndex(i);
            assertTrue(BackpackIds.isValid(id), id);
            assertEquals(i, BackpackIds.index(id));
            assertTrue(seen.add(id), "duplicate " + id);
        }
        assertEquals(BackpackIds.TOTAL, seen.size());
        assertEquals(-1, BackpackIds.index("I000"));
    }

    @Test
    void fromIndexRefusesOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> BackpackIds.fromIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> BackpackIds.fromIndex(BackpackIds.TOTAL));
    }

    @Test
    void theIdOfAnInternalIdIsAlwaysTheSameAndValid() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String id = BackpackIds.of(uuid);
        assertTrue(BackpackIds.isValid(id));
        for (int i = 0; i < 5; i++) {
            assertEquals(id, BackpackIds.of(new UUID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits())));
        }
        assertEquals(id, new BackpackIdentity(uuid).id());
    }

    @Test
    void idsAreSpreadEvenlyOverTheWholeAlphabet() {
        Random rng = new Random(26);
        Map<String, Integer> counts = new HashMap<>();
        Set<Character> firsts = new HashSet<>();
        Set<Character> rests = new HashSet<>();
        int samples = 20000;
        for (int i = 0; i < samples; i++) {
            String id = BackpackIds.of(new UUID(rng.nextLong(), rng.nextLong()));
            assertTrue(BackpackIds.isValid(id), id);
            counts.merge(id, 1, Integer::sum);
            firsts.add(id.charAt(0));
            rests.add(id.charAt(1));
            rests.add(id.charAt(2));
            rests.add(id.charAt(3));
        }
        assertEquals(24, firsts.size());
        assertEquals(32, rests.size());
        // 20000 draws over 786432 ids: nearly all are different and none is drawn many times
        assertTrue(counts.size() > 19000, "only " + counts.size() + " different ids");
        assertTrue(counts.values().stream().mapToInt(Integer::intValue).max().orElse(0) <= 4);
    }

    @Test
    void internalIdsThatDifferInOneBitGetDifferentIds() {
        // no obvious pattern: neighbouring internal ids do not give neighbouring or equal ids
        Set<String> ids = new HashSet<>();
        for (int bit = 0; bit < 64; bit++) {
            ids.add(BackpackIds.of(new UUID(1L << bit, 0L)));
            ids.add(BackpackIds.of(new UUID(0L, 1L << bit)));
        }
        assertTrue(ids.size() >= 120, "only " + ids.size() + " different ids out of 128");
    }
}
