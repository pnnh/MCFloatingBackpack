package pnnh.mcfloatingbackpack.item;

import net.minecraft.world.item.Item;

/**
 * 021: the second expansion tool. An ordinary item: it does nothing in hand or in the world. Only while it sits in the
 * floating panel's right area does the panel offer its trash can panel, so there is nothing to restrict where it can
 * be put (the tool areas accept any item, 016); how many fit in one area is the server's placement rule
 * ({@code ToolAreaRules}). What the trash can holds belongs to the backpack, not to this item.
 */
public final class TrashCanItem extends Item {
    public TrashCanItem(Properties properties) {
        super(properties.stacksTo(1));
    }
}
