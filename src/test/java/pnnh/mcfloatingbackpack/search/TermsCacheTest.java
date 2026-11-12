package pnnh.mcfloatingbackpack.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TermsCacheTest {
    @Test
    void aStoredValueIsFoundAndAMissIsNull() {
        TermsCache<String, Integer> cache = new TermsCache<>(2);
        cache.put("a", 1);
        assertEquals(1, cache.get("a"));
        assertNull(cache.get("b"));
    }

    @Test
    void theLeastRecentlyUsedIsEvictedFirst() {
        TermsCache<String, Integer> cache = new TermsCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a"); // a is now newer than b
        cache.put("c", 3);
        assertEquals(2, cache.size());
        assertEquals(1, cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals(3, cache.get("c"));
    }

    @Test
    void clearEmptiesIt() {
        TermsCache<String, Integer> cache = new TermsCache<>(2);
        cache.put("a", 1);
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
    }

    @Test
    void aCapacityBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new TermsCache<String, Integer>(0));
    }
}
