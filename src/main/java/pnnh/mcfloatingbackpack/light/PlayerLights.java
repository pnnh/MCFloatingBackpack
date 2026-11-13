package pnnh.mcfloatingbackpack.light;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;

/**
 * 052: how bright the light of a player is, from the light item in the bottom tool area of the backpack the B key opens (hand, then back,
 * then inventory; the others do not count). The backpack is only read. This class has no type of any other mod: the dynamic lights mod
 * (see the compat package for it) asks it, and the mod itself does not light anything.
 */
public final class PlayerLights {
    private PlayerLights() {
    }

    /** 0 if the backpack the B key opens has no light in its bottom area (or the player has no backpack). */
    public static int brightnessOf(Player player) {
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null) {
            return 0;
        }
        BackpackContents contents = ref.getStack(player).get(ModDataComponents.BACKPACK_CONTENTS.get());
        if (contents == null) {
            return 0;
        }
        List<ItemStack> bottom = contents.tools().bottom();
        int index = ToolLightRules.firstLight(bottom, LightTags::isLight);
        return index < 0 ? 0 : brightnessOf(bottom.get(index));
    }

    /** The brightness of one light item (see {@link ToolLights#level}). */
    public static int brightnessOf(ItemStack stack) {
        Item item = stack.getItem();
        boolean block = item instanceof BlockItem;
        int emission = block ? ((BlockItem) item).getBlock().defaultBlockState().getLightEmission() : 0;
        return ToolLights.level(BuiltInRegistries.ITEM.getKey(item).toString(), LightTags.isLight(stack), emission, block);
    }
}
