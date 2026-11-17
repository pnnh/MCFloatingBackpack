package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 055: the fifth expansion tool of the right area. An ordinary item: it does nothing in hand or in the world. Only while it sits in the floating
 * panel's right area does a left click on it ask the server to open a vanilla furnace screen; what is in the furnace (and how far it has
 * smelted) is kept on this item itself, see {@code tool.furnace.FurnaceState}. That the right area takes at most one is the server's placement
 * rule ({@code ToolSingleRules}).
 */
public final class MobileFurnaceItem extends Item {
    public MobileFurnaceItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mcfloatingbackpack.mobile_furnace.tooltip"));
    }
}
