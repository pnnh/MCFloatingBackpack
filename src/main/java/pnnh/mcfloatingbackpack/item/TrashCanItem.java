package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

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

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mcfloatingbackpack.trash_can.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
