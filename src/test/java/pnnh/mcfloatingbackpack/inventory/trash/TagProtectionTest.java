package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;

/** 022: only the trash tag is blocked, and only when a protected item is among the selected ones. */
class TagProtectionTest {
    @Test
    void trashIsBlockedWhenAProtectedItemIsSelected() {
        assertTrue(TagProtection.blocks("trash", 1));
        assertTrue(TagProtection.blocks("trash", 5));
    }

    @Test
    void trashIsFineWithoutProtectedItems() {
        assertFalse(TagProtection.blocks("trash", 0));
    }

    @Test
    void otherTagsAreNeverBlocked() {
        assertFalse(TagProtection.blocks("food", 3));
        assertFalse(TagProtection.blocks("Trash", 3));
        assertFalse(TagProtection.blocks("trashy", 3));
        assertFalse(TagProtection.blocks(null, 3));
    }

    @Test
    void theBlockedTagIsTheTrashPreset() {
        assertTrue(PresetTags.isPreset(TagProtection.TRASH));
    }
}
