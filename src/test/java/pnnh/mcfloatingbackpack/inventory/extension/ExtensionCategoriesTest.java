package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 038: the universal category sits after the ten category extensions and is not one of them. */
class ExtensionCategoriesTest {
    @Test
    void theTenCategoryExtensionsAreUnchanged() {
        assertEquals(List.of("food", "tool", "weapon", "armor", "ore", "crop", "wood", "potion", "redstone", "disc"), ExtensionCategories.IDS);
        assertFalse(ExtensionCategories.IDS.contains(ExtensionCategories.GENERAL));
    }

    @Test
    void allIsTheTenAndThenGeneral() {
        assertEquals(11, ExtensionCategories.ALL.size());
        assertEquals(ExtensionCategories.IDS, ExtensionCategories.ALL.subList(0, 10));
        assertEquals("general", ExtensionCategories.ALL.get(10));
        assertEquals("general", ExtensionCategories.GENERAL);
    }

    @Test
    void generalIsLastButKnown() {
        for (String id : ExtensionCategories.IDS) {
            assertTrue(ExtensionCategories.orderOf("general") > ExtensionCategories.orderOf(id), id);
        }
        assertTrue(ExtensionCategories.orderOf("zzz") > ExtensionCategories.orderOf("general"));
        assertTrue(ExtensionCategories.isKnown("general"));
        assertFalse(ExtensionCategories.isKnown("plant"));
        assertFalse(ExtensionCategories.isKnown(null));
    }
}
