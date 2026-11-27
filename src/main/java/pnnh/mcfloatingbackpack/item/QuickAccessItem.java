package pnnh.mcfloatingbackpack.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.loading.FMLEnvironment;

import pnnh.mcfloatingbackpack.client.FloatingBackpackKeys;
import pnnh.mcfloatingbackpack.inventory.quick.QuickAccessPlace;

/**
 * 067, 068: the quick-access tool, one for each backpack tier. It does nothing in hand, in the world, or outside the bottom
 * area. The quick-access key opens only when the bottom area holds the item of the same tier as the backpack.
 */
public final class QuickAccessItem extends Item {
    private final BackpackTier tier;

    public QuickAccessItem(BackpackTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public BackpackTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
                        "item.mcfloatingbackpack.quick_access.tooltip",
                        Component.translatable("item.mcfloatingbackpack." + tier.id()),
                        boundKey())
                .withStyle(ChatFormatting.GRAY));
    }

    /** The bound key on the client. A dedicated server has no key bindings, so it names the control instead. */
    private static Component boundKey() {
        if (FMLEnvironment.dist.isClient()) {
            return FloatingBackpackKeys.quickAccessKey();
        }
        return Component.translatable("key.mcfloatingbackpack.quick_access");
    }

    /** Leather keeps the 067 id. The other tiers are {@code <tier>_quick_access}. */
    public static String idOf(BackpackTier tier) {
        return tier == BackpackTier.LEATHER ? "quick_access" : tier.name().toLowerCase(Locale.ROOT) + "_quick_access";
    }

    /** The tier of a quick-access stack, or {@code null} for anything else. */
    public static BackpackTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof QuickAccessItem item ? item.tier() : null;
    }

    /**
     * Whether this stack may enter {@code slot} of the stored tool list. Not the bottom area, or not a quick-access item:
     * always allowed. A different tier is refused even when it replaces the stack already there.
     */
    public static QuickAccessPlace.Verdict placement(
            boolean bottom, BackpackTier backpack, ItemStack incoming, int slot, List<ItemStack> stored) {
        List<BackpackTier> tiers = new ArrayList<>(stored.size());
        for (ItemStack stack : stored) {
            tiers.add(tierOf(stack));
        }
        return QuickAccessPlace.check(bottom, backpack, tierOf(incoming), slot, tiers);
    }
}
