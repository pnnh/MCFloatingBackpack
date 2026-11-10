package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;

/**
 * 035: whether an item stack fits a category, from two sources of which either is enough: the data tag
 * {@code mcfloatingbackpack:extension/<id>} (a food also by its food component, a potion by its potion contents), or
 * the backpack's own tags (018), which give the item the category's id (the player did it with the tag manager). Only asked
 * when something is placed.
 */
public final class ItemCategoryMatcher implements CategoryMatcher<ItemStack> {
    private static final Map<String, TagKey<Item>> KEYS = new HashMap<>();

    static {
        for (String id : ExtensionCategories.IDS) {
            KEYS.put(id, TagKey.create(
                    Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "extension/" + id)));
        }
    }

    private final ItemTags tags;

    /** @param tags the tags of the backpack the stack is going into */
    public ItemCategoryMatcher(ItemTags tags) {
        this.tags = tags == null ? ItemTags.EMPTY : tags;
    }

    @Override
    public boolean matches(String category, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        // 038: a universal row takes anything that is not empty: no tag, no manual tag.
        if (ExtensionCategories.GENERAL.equals(category)) {
            return true;
        }
        TagKey<Item> key = KEYS.get(category);
        if (key == null) {
            return false;
        }
        if (stack.is(key)) {
            return true;
        }
        if (category.equals("food") && stack.has(DataComponents.FOOD)) {
            return true;
        }
        if (category.equals("potion") && stack.has(DataComponents.POTION_CONTENTS)) {
            return true;
        }
        List<String> manual = tags.tagsOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return manual.contains(category);
    }
}
