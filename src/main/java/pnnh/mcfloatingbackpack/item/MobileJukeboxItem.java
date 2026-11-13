package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 053: the third expansion tool of the right area. An ordinary item: it does nothing in hand or in the world. Only while it sits in the floating
 * panel's right area does the panel offer its player page, so there is nothing to restrict where it can be put (the tool areas accept any
 * item, 016); that the right area takes at most one is the server's placement rule ({@code ToolSingleRules}). What it plays is read from the
 * backpack's disc extension rows, not kept in this item.
 */
public final class MobileJukeboxItem extends Item {
    public MobileJukeboxItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mcfloatingbackpack.mobile_jukebox.tooltip"));
    }
}
