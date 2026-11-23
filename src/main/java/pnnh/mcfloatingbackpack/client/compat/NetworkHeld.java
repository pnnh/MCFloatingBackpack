package pnnh.mcfloatingbackpack.client.compat;

import net.minecraft.world.item.ItemStack;

/**
 * 060 (fix after the first real-machine test): an item the player has picked up on a network terminal's own "cursor" without it being the menu's carried stack.
 * Integrated Terminals keeps a picked-up entry of its list as an "active entry" with an amount; the screen draws it at the mouse and
 * sends the drop to the server only when it is clicked into a slot. The floating panel has to know about it to draw it above itself and to
 * take the drop. {@code what} is one of the item, with its data; {@code amount} is how many are held.
 */
public record NetworkHeld(ItemStack what, int amount) {
    /** The held stack as it is drawn on the cursor. */
    public ItemStack asStack() {
        return what.copyWithCount(Math.max(1, Math.min(amount, 99)));
    }
}
