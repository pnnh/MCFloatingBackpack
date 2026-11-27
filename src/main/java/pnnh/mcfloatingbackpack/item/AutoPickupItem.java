package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 024: the third expansion tool. An ordinary item: it does nothing in hand or in the world. Only while it sits in the
 * floating panel's left (input) area does the player's pickup go to the backpack and does the panel offer its pickup
 * blacklist page, so there is nothing to restrict where it can be put (the tool areas accept any item, 016); how many fit
 * in the left area is the server's placement rule ({@code ToolAreaRules}). The blacklist belongs to the backpack, not to
 * this item.
 */
public final class AutoPickupItem extends Item {
    public AutoPickupItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mcfloatingbackpack.auto_pickup.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
