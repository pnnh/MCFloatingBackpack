package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;

/**
 * 038: a universal extension, one item for each backpack tier (they differ in their recipes, not in what they do). An ordinary
 * item that does nothing in hand or in the world; it stacks to one. Put in the left or the right area of the floating panel of a
 * backpack of its own tier it adds a row of nine cells that takes any item; anywhere else, or in a backpack of another tier, it
 * is an ordinary item. At most one counts for each of the two areas (see {@code PlacedPacks}).
 */
public final class UniversalExtensionItem extends CategoryExtensionItem {
    private final BackpackTier tier;

    public UniversalExtensionItem(BackpackTier tier, Properties properties) {
        super(ExtensionCategories.GENERAL, properties);
        this.tier = tier;
    }

    public BackpackTier tier() {
        return tier;
    }

    /** The tier as the plain id that {@code ExtensionPacks.tierOf} and {@code PlacedPacks} use: {@code leather} ... {@code netherite}. */
    public String tierId() {
        return tier.name().toLowerCase();
    }

    /** Not the line of the category extensions ("only takes X"): this one says where it works and which backpacks it is for. */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.mcfloatingbackpack.universal_extension.tooltip",
                        Component.translatable("tier.mcfloatingbackpack." + tierId()))
                .withStyle(ChatFormatting.GRAY));
    }
}
