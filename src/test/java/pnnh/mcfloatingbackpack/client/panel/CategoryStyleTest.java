package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;

class CategoryStyleTest {
    @Test
    void everyCategoryHasAStyle() {
        for (String id : ExtensionCategories.IDS) {
            assertTrue(CategoryStyle.known(id), id);
            assertEquals(id + "_extension", CategoryStyle.ghostItemId(id));
        }
    }

    @Test
    void theTenEdgeColoursAreAllDifferent() {
        Set<Integer> seen = new HashSet<>();
        for (String id : ExtensionCategories.IDS) {
            assertTrue(seen.add(CategoryStyle.edge(id)), id);
        }
    }

    @Test
    void theEdgeIsOpaqueAndTheFillIsTheSameColourTranslucent() {
        for (String id : ExtensionCategories.IDS) {
            assertEquals(0xFF, CategoryStyle.edge(id) >>> 24, id);
            int fill = CategoryStyle.fill(id);
            assertEquals(CategoryStyle.edge(id) & 0xFFFFFF, fill & 0xFFFFFF, id);
            int alpha = fill >>> 24;
            assertTrue(alpha > 0x10 && alpha < 0xC0, id + " alpha " + alpha);
        }
    }

    @Test
    void noEdgeColourIsTheColourOfTheSlotItself() {
        for (String id : ExtensionCategories.IDS) {
            assertNotEquals(0xFF8B8B8B, CategoryStyle.edge(id), id);
        }
    }

    @Test
    void anUnknownCategoryIsNeutralAndHasNoGhost() {
        assertFalse(CategoryStyle.known("zzz"));
        assertEquals(0xFF808080, CategoryStyle.edge("zzz"));
        assertNull(CategoryStyle.ghostItemId("zzz"));
        assertNull(CategoryStyle.ghostItemId(null));
    }

    @Test
    void theUniversalCategoryHasAStyleOfItsOwnAndNoGhost() {
        assertTrue(CategoryStyle.known("general"));
        assertNull(CategoryStyle.ghostItemId("general"));
        for (String id : ExtensionCategories.IDS) {
            assertNotEquals(CategoryStyle.edge(id), CategoryStyle.edge("general"), id);
        }
        assertNotEquals(0xFF8B8B8B, CategoryStyle.edge("general"));
        assertEquals(0xFF, CategoryStyle.edge("general") >>> 24);
        assertEquals(CategoryStyle.edge("general") & 0xFFFFFF, CategoryStyle.fill("general") & 0xFFFFFF);
        // a near white at the alpha of the ten could not be seen on the slot's grey: the universal fill is the stronger of the eleven
        int generalAlpha = CategoryStyle.fill("general") >>> 24;
        for (String id : ExtensionCategories.IDS) {
            assertTrue(generalAlpha > (CategoryStyle.fill(id) >>> 24), id);
        }
        assertTrue(generalAlpha < 0xC0);
    }
}

