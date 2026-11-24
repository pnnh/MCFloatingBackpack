package pnnh.mcfloatingbackpack.item;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 065: the combined backpack. It holds nothing and carries no data: worn (the chest slot, or the dedicated slot of Curios or
 * Accessories), it connects the floating backpacks the player carries that are of its tier or lower, and the combined key opens a
 * panel with one tab for each of them. There is no model on the back, only the item's flat icon.
 *
 * <p>Like a backpack it is not an {@code ArmorItem}; sneak-use puts it on, replacing whatever is in the chest slot.
 */
public class CombinedBackpackItem extends Item implements Equipable {
    private final BackpackTier tier;

    public CombinedBackpackItem(BackpackTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public BackpackTier tier() {
        return tier;
    }

    public static Properties baseProperties() {
        return new Properties().stacksTo(1);
    }

    /** The registry id of a tier's combined backpack: {@code <tier>_combined_backpack}. */
    public static String idOf(BackpackTier tier) {
        return tier.name().toLowerCase(Locale.ROOT) + "_combined_backpack";
    }

    public static boolean isCombined(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CombinedBackpackItem;
    }

    /** The tier of a combined backpack, or {@code null} for anything else. */
    public static BackpackTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof CombinedBackpackItem item ? item.tier() : null;
    }

    /** It holds no data, but a combined backpack inside a container item would only be something to lose: same rule as a floating backpack (064). */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.mcfloatingbackpack.combined").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
            return swapWithEquipmentSlot(this, level, player, hand);
        }
        return InteractionResultHolder.pass(stack);
    }
}
