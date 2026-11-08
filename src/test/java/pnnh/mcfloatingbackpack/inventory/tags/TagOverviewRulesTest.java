package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.tags.TagOverviewRequest.Op;

/** 025: what each request from the tag overview does to the tag book (or why it is refused). */
class TagOverviewRulesTest {
    private static TagBook<String> book() {
        TagBook<String> b = new TagBook<>();
        b = b.add(List.of("log"), "wood").add(List.of("log"), "build");
        b = b.add(List.of("stone"), "stone");
        return b;
    }

    private static TagBook<String> apply(TagBook<String> b, Op op, String key, String tag, boolean registered, boolean isProtected) {
        return TagOverviewRules.apply(b, op, key, tag, registered, isProtected);
    }

    @Test
    void addAppendsToACategoryThatAlreadyHasTags() {
        TagBook<String> next = apply(book(), Op.ADD, "log", "keep", true, false);
        assertEquals(List.of("wood", "build", "keep"), next.tagsOf("log"));
    }

    @Test
    void addToACategoryWithoutTagsIsRefused() {
        assertNull(apply(book(), Op.ADD, "dirt", "keep", true, false));
    }

    @Test
    void addToAnItemThatIsNoLongerInTheGameIsRefused() {
        assertNull(apply(book(), Op.ADD, "log", "keep", false, false));
    }

    @Test
    void theTrashTagIsRefusedForAProtectedItemButOtherTagsAreNot() {
        assertNull(apply(book(), Op.ADD, "log", "trash", true, true));
        assertEquals(List.of("wood", "build", "keep"), apply(book(), Op.ADD, "log", "keep", true, true).tagsOf("log"));
        assertEquals(List.of("wood", "build", "trash"), apply(book(), Op.ADD, "log", "trash", true, false).tagsOf("log"));
    }

    @Test
    void addingATagTheCategoryAlreadyHasChangesNothing() {
        TagBook<String> start = book();
        assertEquals(start.asMap(), apply(start, Op.ADD, "log", "wood", true, false).asMap());
    }

    @Test
    void addBeyondTenTagsIsRefused() {
        TagBook<String> b = new TagBook<>();
        for (int i = 0; i < TagRules.MAX_TAGS_PER_ITEM; i++) {
            b = b.add(List.of("log"), "t" + i);
        }
        assertNull(apply(b, Op.ADD, "log", "eleven", true, false));
        assertEquals(b.asMap(), apply(b, Op.ADD, "log", "t3", true, false).asMap());
    }

    @Test
    void removeTakesOneTagOff() {
        TagBook<String> next = apply(book(), Op.REMOVE, "log", "wood", true, false);
        assertEquals(List.of("build"), next.tagsOf("log"));
    }

    @Test
    void removingTheLastTagMakesTheCategoryDisappear() {
        TagBook<String> next = apply(book(), Op.REMOVE, "stone", "stone", true, false);
        assertTrue(next.tagsOf("stone").isEmpty());
        assertEquals(1, next.asMap().size());
    }

    @Test
    void removeOfAMissingCategoryOrTagChangesNothing() {
        TagBook<String> start = book();
        assertEquals(start.asMap(), apply(start, Op.REMOVE, "dirt", "wood", true, false).asMap());
        assertEquals(start.asMap(), apply(start, Op.REMOVE, "log", "nope", true, false).asMap());
    }

    @Test
    void removeWorksForProtectedAndMissingItemsToo() {
        assertEquals(List.of("build"), apply(book(), Op.REMOVE, "log", "wood", false, true).tagsOf("log"));
    }

    @Test
    void clearRemovesTheWholeCategory() {
        TagBook<String> next = apply(book(), Op.CLEAR, "log", "", true, false);
        assertTrue(next.tagsOf("log").isEmpty());
        assertEquals(List.of("stone"), next.tagsOf("stone"));
    }

    @Test
    void clearOfAMissingCategoryChangesNothing() {
        TagBook<String> start = book();
        assertEquals(start.asMap(), apply(start, Op.CLEAR, "dirt", "", true, false).asMap());
    }

    @Test
    void clearWorksForProtectedAndMissingItemsToo() {
        assertTrue(apply(book(), Op.CLEAR, "log", "", false, true).tagsOf("log").isEmpty());
    }

    @Test
    void theGivenBookIsNeverChanged() {
        TagBook<String> start = book();
        apply(start, Op.ADD, "log", "keep", true, false);
        apply(start, Op.REMOVE, "log", "wood", true, false);
        apply(start, Op.CLEAR, "log", "", true, false);
        assertEquals(List.of("wood", "build"), start.tagsOf("log"));
    }
}
