package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 054: the fourth expansion tool of the right area. An ordinary item: it does nothing in hand or in the world. Only while it sits in the floating
 * panel's right area does a left click on it ask the server to open a vanilla crafting screen, so there is nothing to restrict where it can be
 * put (the tool areas accept any item, 016); that the right area takes at most one is the server's placement rule ({@code ToolSingleRules}).
 */
public final class MobileCraftingTableItem extends Item {
    public MobileCraftingTableItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.mcfloatingbackpack.mobile_crafting_table.tooltip"));
    }
}
