package pnnh.mcfloatingbackpack.inventory.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 024: the pickup blacklist's rules (limit, cleaning, matching). Plain Java: the test class path has no Minecraft. */
class PickupBlacklistTest {
    private static PickupBlacklist full() {
        PickupBlacklist list = PickupBlacklist.EMPTY;
        for (int i = 0; i < PickupBlacklist.MAX_TAGS; i++) {
            list = list.add("tag" + i);
        }
        return list;
    }

    @Test
    void emptyHasNoTags() {
        assertTrue(PickupBlacklist.EMPTY.tags().isEmpty());
        assertEquals(0, PickupBlacklist.EMPTY.size());
        assertFalse(PickupBlacklist.EMPTY.isFull());
    }

    @Test
    void addAppendsAtTheEnd() {
        PickupBlacklist list = PickupBlacklist.EMPTY.add("stone").add("trash");
        assertEquals(List.of("stone", "trash"), list.tags());
        assertTrue(list.contains("stone"));
        assertFalse(list.contains("wood"));
    }

    @Test
    void addExistingIsIdempotentNotAnError() {
        PickupBlacklist list = PickupBlacklist.EMPTY.add("stone");
        assertSame(list, list.add("stone"));
    }

    @Test
    void addInvalidTagIsRefused() {
        assertNull(PickupBlacklist.EMPTY.add(""));
        assertNull(PickupBlacklist.EMPTY.add("has space"));
        assertNull(PickupBlacklist.EMPTY.add("Upper"));
        assertNull(PickupBlacklist.EMPTY.add("a".repeat(17)));
        assertNull(PickupBlacklist.EMPTY.add(null));
        assertEquals(PickupBlacklist.EMPTY.add("a".repeat(16)).size(), 1);
    }

    @Test
    void addBeyondTheLimitIsRefusedButAnExistingTagStillSucceeds() {
        PickupBlacklist list = full();
        assertEquals(64, list.size());
        assertTrue(list.isFull());
        assertNull(list.add("one-more"));
        assertSame(list, list.add("tag3"));
    }

    @Test
    void removeDropsTheTagAndIgnoresAMissingOne() {
        PickupBlacklist list = PickupBlacklist.EMPTY.add("stone").add("wood");
        assertEquals(List.of("wood"), list.remove("stone").tags());
        assertSame(list, list.remove("food"));
    }

    @Test
    void matchesWhenAnyOfTheItemsTagsIsListed() {
        PickupBlacklist list = PickupBlacklist.EMPTY.add("stone").add("trash");
        assertTrue(list.matches(List.of("wood", "trash")));
        assertTrue(list.matches(List.of("stone")));
        assertFalse(list.matches(List.of("wood", "food")));
        assertFalse(list.matches(List.of()));
        assertFalse(PickupBlacklist.EMPTY.matches(List.of("stone")));
    }

    @Test
    void sanitizeDropsInvalidAndDuplicateAndCutsToTheLimitKeepingOrder() {
        List<String> raw = new ArrayList<>(List.of("b", "Bad", "a", "b", "has space", "", "c"));
        assertEquals(List.of("b", "a", "c"), PickupBlacklist.sanitize(raw));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add("t" + i);
        }
        List<String> cut = PickupBlacklist.sanitize(many);
        assertEquals(64, cut.size());
        assertEquals("t0", cut.get(0));
        assertEquals("t63", cut.get(63));
    }

    @Test
    void theListIsImmutableAndCopiedOnConstruction() {
        List<String> source = new ArrayList<>(List.of("stone"));
        PickupBlacklist list = new PickupBlacklist(source);
        source.add("wood");
        assertEquals(List.of("stone"), list.tags());
        assertThrows(UnsupportedOperationException.class, () -> list.tags().add("x"));
    }

    @Test
    void theConstructorCleansForeignData() {
        PickupBlacklist list = new PickupBlacklist(List.of("ok", "NOT OK", "ok"));
        assertEquals(List.of("ok"), list.tags());
    }
}
