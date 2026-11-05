package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TagBookTest {
    private static TagBook<String> book(String key, String... tags) {
        TagBook<String> b = new TagBook<>();
        for (String tag : tags) {
            b = b.add(List.of(key), tag);
        }
        return b;
    }

    @Test
    void addKeepsOrderAndIgnoresDuplicates() {
        TagBook<String> b = book("log", "wood", "build", "wood");
        assertEquals(List.of("wood", "build"), b.tagsOf("log"));
    }

    @Test
    void tagsOfReturnsACopy() {
        TagBook<String> b = book("log", "wood");
        b.tagsOf("log").add("hack");
        assertEquals(List.of("wood"), b.tagsOf("log"));
    }

    @Test
    void commonIsTheIntersectionInFirstCategoryOrder() {
        TagBook<String> b = new TagBook<String>()
                .add(List.of("a"), "x").add(List.of("a"), "y").add(List.of("a"), "z")
                .add(List.of("b"), "z").add(List.of("b"), "x");
        assertEquals(List.of("x", "z"), b.common(List.of("a", "b")));
        assertEquals(List.of("x", "y", "z"), b.common(List.of("a")));
        assertEquals(List.of(), b.common(List.of("a", "none")));
        assertEquals(List.of(), b.common(List.of()));
    }

    @Test
    void fullListsCategoriesWithTenTags() {
        TagBook<String> b = new TagBook<>();
        for (int i = 0; i < 10; i++) {
            b = b.add(List.of("full"), "t" + i);
        }
        b = b.add(List.of("nine"), "t0");
        assertEquals(List.of("full"), b.full(List.of("full", "nine", "empty")));
    }

    @Test
    void canAddIsFalseWhenAnyCategoryLacksTheTagAndIsFull() {
        TagBook<String> b = new TagBook<>();
        for (int i = 0; i < 10; i++) {
            b = b.add(List.of("full"), "t" + i);
        }
        assertFalse(b.canAdd(List.of("full", "other"), "new"));
        assertTrue(b.canAdd(List.of("full", "other"), "t3"), "a full category that already has it is not over the limit");
        assertTrue(b.canAdd(List.of("other"), "new"));
    }

    @Test
    void aRejectedAddChangesNothing() {
        TagBook<String> b = new TagBook<>();
        for (int i = 0; i < 10; i++) {
            b = b.add(List.of("full"), "t" + i);
        }
        TagBook<String> before = b;
        assertNull(b.addOrNull(List.of("full", "other"), "new"));
        assertEquals(before.tagsOf("full"), b.tagsOf("full"));
        assertEquals(List.of(), b.tagsOf("other"));
    }

    @Test
    void addToSeveralCategoriesAtOnce() {
        TagBook<String> b = new TagBook<String>().add(List.of("a", "b", "c"), "wood");
        assertEquals(List.of("wood"), b.tagsOf("a"));
        assertEquals(List.of("wood"), b.tagsOf("b"));
        assertEquals(List.of("wood"), b.tagsOf("c"));
    }

    @Test
    void ninthToTenthIsAllowed() {
        TagBook<String> b = new TagBook<>();
        for (int i = 0; i < 9; i++) {
            b = b.add(List.of("k"), "t" + i);
        }
        assertNotNull(b.addOrNull(List.of("k"), "t9"));
    }

    @Test
    void removeSkipsCategoriesWithoutTheTag() {
        TagBook<String> b = new TagBook<String>().add(List.of("a", "b"), "x").add(List.of("a"), "y");
        TagBook<String> after = b.remove(List.of("a", "b", "none"), "x");
        assertEquals(List.of("y"), after.tagsOf("a"));
        assertEquals(List.of(), after.tagsOf("b"));
        assertEquals(List.of("x", "y"), b.tagsOf("a"), "the original is untouched");
    }

    @Test
    void sanitizeDropsInvalidAndDuplicateTagsAndCapsAtTen() {
        List<String> raw = new java.util.ArrayList<>(List.of("wood", "Wood", "a b", "wood"));
        for (int i = 0; i < 20; i++) {
            raw.add("t" + i);
        }
        java.util.Map<String, List<String>> out = TagBook.sanitize(java.util.Map.of("log", raw), 1024);
        assertEquals(10, out.get("log").size());
        assertEquals("wood", out.get("log").get(0));
        assertEquals("t0", out.get("log").get(1));
    }

    @Test
    void sanitizeDropsEmptyCategoriesAndCapsTheCategoryCount() {
        java.util.Map<String, List<String>> raw = new java.util.LinkedHashMap<>();
        raw.put("none", List.of("Bad"));
        for (int i = 0; i < 5; i++) {
            raw.put("k" + i, List.of("a"));
        }
        java.util.Map<String, List<String>> out = TagBook.sanitize(raw, 3);
        assertEquals(3, out.size());
        assertTrue(!out.containsKey("none"));
    }

    @Test
    void randomOperationsKeepTheInvariants() {
        Random random = new Random(42);
        List<String> keys = List.of("a", "b", "c", "d");
        TagBook<String> b = new TagBook<>();
        for (int step = 0; step < 200; step++) {
            String tag = "t" + random.nextInt(14);
            List<String> chosen = List.of(keys.get(random.nextInt(4)), keys.get(random.nextInt(4)));
            if (random.nextBoolean()) {
                TagBook<String> next = b.addOrNull(chosen, tag);
                if (next != null) {
                    b = next;
                }
            } else {
                b = b.remove(chosen, tag);
            }
            for (String key : keys) {
                List<String> tags = b.tagsOf(key);
                assertTrue(tags.size() <= TagRules.MAX_TAGS_PER_ITEM);
                assertEquals(tags.size(), Set.copyOf(tags).size());
            }
        }
    }
}
