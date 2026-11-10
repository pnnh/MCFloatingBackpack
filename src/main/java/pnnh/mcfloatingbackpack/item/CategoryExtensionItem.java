package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;

/**
 * 036: a category extension. An ordinary item that does nothing in hand or in the world; it stacks to one. Put in any of the
 * three tool areas of the floating panel it adds a row of nine cells to that backpack, which only takes items of its
 * category (035). Which item is a pack of which category is what {@code ExtensionPacks} is told at start-up.
 */
public class CategoryExtensionItem extends Item {
    private final String category;

    /** @param category one of {@code ExtensionCategories.IDS} (a universal extension, 038, passes {@code ExtensionCategories.GENERAL}) */
    public CategoryExtensionItem(String category, Properties properties) {
        super(properties.stacksTo(1));
        this.category = category;
    }

    public String category() {
        return category;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
                        "item.mcfloatingbackpack.category_extension.tooltip",
                        Component.translatable(PresetTags.langKey(category)))
                .withStyle(ChatFormatting.GRAY));
    }
}
