package pnnh.mcfloatingbackpack.inventory.extension;

import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;

/**
 * 038: the language key of a category's name, for the row's tooltip and the hint about hidden items. The ten category extensions
 * are named after the tag presets (food, tool...); the universal category is no preset and has a key of its own. Pure logic: only
 * strings.
 */
public final class CategoryNames {
    public static final String GENERAL_KEY = "category.mcfloatingbackpack.general";

    private CategoryNames() {
    }

    public static String langKey(String category) {
        return ExtensionCategories.GENERAL.equals(category) ? GENERAL_KEY : PresetTags.langKey(category);
    }
}
