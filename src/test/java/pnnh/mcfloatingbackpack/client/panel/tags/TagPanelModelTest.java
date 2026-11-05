package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelModel.Status;
import pnnh.mcfloatingbackpack.inventory.tags.TagBook;

class TagPanelModelTest {
    /** Slot i holds category CATS[i]; "" is an empty slot. */
    private static final String[] CATS = {"log", "stone", "log", "", "bread", "stone"};

    private static String at(int slot) {
        String c = CATS[slot];
        return c.isEmpty() ? null : c;
    }

    private static TagBook<String> book() {
        return new TagBook<String>()
                .add(List.of("log"), "wood").add(List.of("log"), "build")
                .add(List.of("stone"), "build").add(List.of("stone"), "stone")
                .add(List.of("bread"), "food");
    }

    private static TagPanelModel<String> model(List<Integer> selected, TagBook<String> book, boolean tool) {
        return TagPanelModel.of(selected, TagPanelModelTest::at, book, tool);
    }

    @Test
    void oneCategoryShowsAllItsTags() {
        TagPanelModel<String> m = model(List.of(0), book(), true);
        assertEquals(Status.ITEMS, m.status());
        assertEquals(List.of("log"), m.categories());
        assertEquals(List.of("wood", "build"), m.commonTags());
        assertTrue(m.canAdd());
    }

    @Test
    void severalCategoriesShowOnlyTheCommonTags() {
        TagPanelModel<String> m = model(List.of(0, 1, 4), book(), true);
        assertEquals(List.of("log", "stone", "bread"), m.categories());
        assertEquals(List.of(), m.commonTags());
        TagPanelModel<String> two = model(List.of(0, 1), book(), true);
        assertEquals(List.of("build"), two.commonTags());
    }

    @Test
    void sameItemInSeveralSlotsCountsOnce() {
        TagPanelModel<String> m = model(List.of(0, 2), book(), true);
        assertEquals(List.of("log"), m.categories());
        assertEquals(List.of("wood", "build"), m.commonTags());
    }

    @Test
    void emptySlotsAreIgnored() {
        TagPanelModel<String> m = model(List.of(3, 4), book(), true);
        assertEquals(List.of("bread"), m.categories());
        assertEquals(List.of("food"), m.commonTags());
    }

    @Test
    void noItemsSelectedMeansNothingToAddTo() {
        TagPanelModel<String> none = model(List.of(), book(), true);
        assertEquals(Status.NO_ITEMS, none.status());
        assertFalse(none.canAdd());
        TagPanelModel<String> onlyEmpty = model(List.of(3), book(), true);
        assertEquals(Status.NO_ITEMS, onlyEmpty.status());
        assertFalse(onlyEmpty.canAdd());
    }

    @Test
    void aFullCategoryBlocksAddingAndIsListed() {
        TagBook<String> b = new TagBook<>();
        for (int i = 0; i < 10; i++) {
            b = b.add(List.of("log"), "t" + i);
        }
        b = b.add(List.of("stone"), "x");
        TagPanelModel<String> m = model(List.of(0, 1), b, true);
        assertEquals(List.of("log"), m.fullItems());
        assertFalse(m.canAdd());
        TagPanelModel<String> stoneOnly = model(List.of(1), b, true);
        assertTrue(stoneOnly.canAdd());
    }

    @Test
    void withoutTheToolNothingCanBeAdded() {
        assertFalse(model(List.of(0), book(), false).canAdd());
    }

    @Test
    void mapBackedBookWorksToo() {
        TagBook<String> b = new TagBook<>(Map.of("log", List.of("wood")));
        assertEquals(List.of("wood"), model(List.of(0), b, true).commonTags());
    }

    // 022: the protection list. Categories "chest" and "egg" are protected in these tests.
    private static final java.util.Set<String> PROTECTED = java.util.Set.of("chest", "egg");

    private static TagPanelModel<String> protectedModel(String... categories) {
        List<Integer> slots = new java.util.ArrayList<>();
        for (int i = 0; i < categories.length; i++) {
            slots.add(i);
        }
        return TagPanelModel.of(
                slots, i -> categories[i].isEmpty() ? null : categories[i], new TagBook<String>(), true, PROTECTED::contains);
    }

    @Test
    void protectedCategoriesAreListedOnceInSelectionOrder() {
        TagPanelModel<String> m = protectedModel("log", "egg", "chest", "egg");
        assertEquals(List.of("egg", "chest"), m.protectedItems());
    }

    @Test
    void trashIsBlockedOnlyWhenAProtectedCategoryIsSelected() {
        assertTrue(protectedModel("log", "chest").blocks("trash"));
        assertFalse(protectedModel("log", "stone").blocks("trash"));
        assertFalse(protectedModel("log", "").blocks("trash"));
    }

    @Test
    void otherTagsAreNeverBlockedAndTheAddButtonKeepsItsMeaning() {
        TagPanelModel<String> m = protectedModel("chest", "log");
        assertFalse(m.blocks("food"));
        assertFalse(m.blocks("wood"));
        assertTrue(m.canAdd());
    }

    @Test
    void anEmptySelectionHasNothingProtected() {
        TagPanelModel<String> m = protectedModel("", "");
        assertEquals(Status.NO_ITEMS, m.status());
        assertEquals(List.of(), m.protectedItems());
        assertFalse(m.blocks("trash"));
    }

    @Test
    void theOldFactoryProtectsNothing() {
        assertFalse(model(List.of(0, 1), book(), true).blocks("trash"));
        assertEquals(List.of(), model(List.of(0), book(), true).protectedItems());
    }
}
