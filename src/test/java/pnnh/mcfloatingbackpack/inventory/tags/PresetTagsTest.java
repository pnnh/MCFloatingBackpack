package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

class PresetTagsTest {
    @Test
    void theThirteenPresetsInOrder() {
        assertEquals(
                List.of("stone", "wood", "food", "ore", "trash", "tool", "weapon", "armor", "plant", "redstone",
                        "crop", "potion", "disc"),
                PresetTags.ids());
    }

    @Test
    void presetsAreUniqueAndAlwaysValidTags() {
        assertEquals(PresetTags.ids().size(), new HashSet<>(PresetTags.ids()).size());
        for (String id : PresetTags.ids()) {
            assertTrue(TagRules.isValid(id), id);
        }
    }

    @Test
    void languageKeyAndLookup() {
        assertEquals("tag.mcfloatingbackpack.preset.wood", PresetTags.langKey("wood"));
        assertTrue(PresetTags.isPreset("trash"));
        assertFalse(PresetTags.isPreset("my_tag"));
        assertFalse(PresetTags.isPreset(null));
    }
}
