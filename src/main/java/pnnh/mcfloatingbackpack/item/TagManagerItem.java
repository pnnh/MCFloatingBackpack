package pnnh.mcfloatingbackpack.item;

import net.minecraft.world.item.Item;

/**
 * 018: the first expansion tool. An ordinary item: it does nothing in hand or in the world. Only while it sits in the
 * floating panel's function area (the bottom row) does the panel offer its tag editor, so there is nothing to
 * restrict where it can be put (the tool areas accept any item, 016).
 */
public final class TagManagerItem extends Item {
    public TagManagerItem(Properties properties) {
        super(properties.stacksTo(1));
    }
}
