package pnnh.mcfloatingbackpack.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/** 022: the item tags this mod reads. They are data: a data pack can add to them or take from them. */
public final class ModTags {
    /**
     * Items that may not be marked as trash (so a dump never takes them): this mod's own items, shulker boxes and a few
     * valuables. Data pack authors can add items of other mods.
     */
    public static final TagKey<Item> NEVER_TRASH = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "never_trash"));

    private ModTags() {
    }
}
