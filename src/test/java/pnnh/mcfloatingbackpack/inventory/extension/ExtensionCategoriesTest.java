package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 038: the universal category sits after the ten category extensions and is not one of them. */
class ExtensionCategoriesTest {
    @Test
    void theFirstTenAreUnchangedAndNineWereAppendedIn051() {
        assertEquals(List.of("food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc"),
                ExtensionCategories.IDS.subList(0, 10));
        assertEquals(List.of("building", "natural", "plant", "decor", "mobdrop", "storage", "transport", "enchant", "meat"),
                ExtensionCategories.IDS.subList(10, 19));
        assertEquals(19, ExtensionCategories.IDS.size());
        for (String id : ExtensionCategories.IDS) {
            assertTrue(id.length() <= ExtensionCategories.MAX_CATEGORY_LENGTH, id);
        }
        assertFalse(ExtensionCategories.IDS.contains(ExtensionCategories.GENERAL));
    }

    @Test
    void allIsTheNineteenAndThenGeneral() {
        assertEquals(20, ExtensionCategories.ALL.size());
        assertEquals(ExtensionCategories.IDS, ExtensionCategories.ALL.subList(0, 19));
        assertEquals("general", ExtensionCategories.ALL.get(19));
        assertEquals("general", ExtensionCategories.GENERAL);
    }

    @Test
    void generalIsLastButKnown() {
        for (String id : ExtensionCategories.IDS) {
            assertTrue(ExtensionCategories.orderOf("general") > ExtensionCategories.orderOf(id), id);
        }
        assertTrue(ExtensionCategories.orderOf("zzz") > ExtensionCategories.orderOf("general"));
        assertTrue(ExtensionCategories.isKnown("general"));
        assertTrue(ExtensionCategories.isKnown("plant")); // a category since 051
        assertFalse(ExtensionCategories.isKnown("zzz"));
        assertFalse(ExtensionCategories.isKnown(null));
    }
}
