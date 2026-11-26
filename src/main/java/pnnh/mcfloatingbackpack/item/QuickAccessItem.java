package pnnh.mcfloatingbackpack.item;

import net.minecraft.world.item.Item;

/**
 * 067: the quick-access tool. An ordinary item: it does nothing in hand, in the world, or in the left and right tool areas.
 * While one sits in the bottom (function) area, the quick-access key can open the grid. At most one in that area.
 */
public final class QuickAccessItem extends Item {
    public QuickAccessItem(Properties properties) {
        super(properties.stacksTo(1));
    }
}
