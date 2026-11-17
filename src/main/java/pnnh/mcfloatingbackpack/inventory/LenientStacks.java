package pnnh.mcfloatingbackpack.inventory;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 056: the list of stacks a backpack keeps in each of its places (the main area, the three tool areas, the extension rows, the trash
 * can), read so that a stack this version cannot read (an item that was removed, a component it does not know) leaves an empty place
 * and does not move the stacks after it; see {@link LenientLists}. 057: how many are counted per thread ({@link LostCells}).
 */
public final class LenientStacks {
    public static final Codec<List<ItemStack>> LIST = LenientLists.of(
            ItemStack.OPTIONAL_CODEC,
            ItemStack.EMPTY,
            lost -> {
                LostCells.add(lost);
                MCFloatingBackpack.LOGGER.warn(
                        "{} stack(s) in a backpack could not be read (an item this version no longer has?) and were left as empty places; the other stacks keep their places", lost);
            });

    private LenientStacks() {
    }
}
