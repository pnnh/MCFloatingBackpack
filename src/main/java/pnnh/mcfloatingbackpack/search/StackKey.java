package pnnh.mcfloatingbackpack.search;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 043: what a provider's answer is cached by: the item and its data components, never the count. Two potions of different effects are
 * different keys (a provider can name them differently), two stacks of the same item are one.
 */
public record StackKey(Item item, DataComponentPatch patch) {
    public static StackKey of(ItemStack stack) {
        DataComponentPatch patch = stack.getComponentsPatch();
        return new StackKey(stack.getItem(), patch.isEmpty() ? DataComponentPatch.EMPTY : patch);
    }
}
