package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;

class CategoryNamesTest {
    @Test
    void theTenCategoriesUseTheTagPresetNames() {
        for (String id : ExtensionCategories.IDS) {
            assertEquals(PresetTags.langKey(id), CategoryNames.langKey(id), id);
        }
    }

    @Test
    void theUniversalCategoryHasItsOwnKey() {
        assertEquals("category.mcfloatingbackpack.general", CategoryNames.langKey("general"));
    }

    @Test
    void anUnknownIdStillGetsAKey() {
        assertNotNull(CategoryNames.langKey("zzz"));
    }
}
