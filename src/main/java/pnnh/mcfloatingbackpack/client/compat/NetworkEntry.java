package pnnh.mcfloatingbackpack.client.compat;

import net.minecraft.world.item.ItemStack;

/**
 * 060: an entry of a network terminal's list that the player Ctrl+clicked: {@code what} is one of the item, with its data;
 * {@code handle} is a number that only the mod's own code understands (AE2: the entry's number in the grid; Integrated Terminals: 0).
 */
public record NetworkEntry(ItemStack what, long handle) {
}
