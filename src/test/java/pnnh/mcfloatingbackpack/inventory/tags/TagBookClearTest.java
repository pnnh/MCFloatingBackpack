package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 025: clearing the tags of whole item categories. */
class TagBookClearTest {
    private static TagBook<String> book() {
        TagBook<String> b = new TagBook<>();
        b = b.add(List.of("log"), "wood").add(List.of("log"), "build");
        b = b.add(List.of("stone"), "stone");
        b = b.add(List.of("dirt"), "trash");
        return b;
    }

    @Test
    void clearingACategoryRemovesItAndLeavesTheOthers() {
        TagBook<String> next = book().clear(List.of("log"));
        assertTrue(next.tagsOf("log").isEmpty());
        assertEquals(List.of("stone"), next.tagsOf("stone"));
        assertEquals(List.of("trash"), next.tagsOf("dirt"));
        assertEquals(2, next.asMap().size());
    }

    @Test
    void clearingSeveralCategoriesAtOnce() {
        TagBook<String> next = book().clear(List.of("log", "dirt"));
        assertEquals(1, next.asMap().size());
        assertEquals(List.of("stone"), next.tagsOf("stone"));
    }

    @Test
    void clearingAMissingCategoryChangesNothing() {
        TagBook<String> start = book();
        assertEquals(start.asMap(), start.clear(List.of("diamond")).asMap());
    }

    @Test
    void theOriginalBookIsNotChanged() {
        TagBook<String> start = book();
        start.clear(List.of("log"));
        assertEquals(List.of("wood", "build"), start.tagsOf("log"));
    }
}
