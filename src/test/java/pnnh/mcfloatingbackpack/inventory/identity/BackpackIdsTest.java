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

/** 026: the three-character backpack ids: format, count, and working an id out of an internal id. */
class BackpackIdsTest {
    @Test
    void thereAre24576Ids() {
        assertEquals(24576, BackpackIds.TOTAL);
        assertEquals(24 * 32 * 32, BackpackIds.TOTAL);
    }

    @Test
    void validIds() {
        assertTrue(BackpackIds.isValid("K7M"));
        assertTrue(BackpackIds.isValid("A22"));
        assertTrue(BackpackIds.isValid("ZZZ"));
        assertTrue(BackpackIds.isValid("B99"));
        assertTrue(BackpackIds.isValid("HJK"));
    }

    @Test
    void invalidIds() {
        assertFalse(BackpackIds.isValid(null));
        assertFalse(BackpackIds.isValid(""));
        assertFalse(BackpackIds.isValid("AB"));
        assertFalse(BackpackIds.isValid("ABCD"));
        assertFalse(BackpackIds.isValid("7AB"), "no digit first");
        assertFalse(BackpackIds.isValid("k7m"), "capital letters only");
        // the confusable characters are left out
        for (String bad : new String[] {"IAB", "OAB", "AIB", "AOB", "ABI", "ABO", "A0B", "A1B", "AB0", "AB1"}) {
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
        assertEquals(-1, BackpackIds.index("I00"));
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
        }
        assertEquals(24, firsts.size());
        assertEquals(32, rests.size());
        // 20000 draws over 24576 ids: most are different and none is drawn many times
        assertTrue(counts.size() > 10000, "only " + counts.size() + " different ids");
        assertTrue(counts.values().stream().mapToInt(Integer::intValue).max().orElse(0) <= 12);
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
