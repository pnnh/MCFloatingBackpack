package pnnh.mcfloatingbackpack.light;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/** 052: the item tag that says which items are lights when they are in the bottom tool area. Data packs and other mods can add to it. */
public final class LightTags {
    public static final TagKey<Item> TOOL_LIGHTS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "tool_lights"));

    private LightTags() {
    }

    public static boolean isLight(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TOOL_LIGHTS);
    }
}
