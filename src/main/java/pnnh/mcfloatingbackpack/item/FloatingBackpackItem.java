package pnnh.mcfloatingbackpack.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import pnnh.mcfloatingbackpack.client.ClientBackpackActions;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;

/**
 * Chest-slot backpack. Sneak-use equips it; a normal use toggles the overlay.
 * Not an {@code ArmorItem}, so the body does not render a vanilla chestplate.
 */
public class FloatingBackpackItem extends Item implements Equipable {
    private final BackpackTier tier;

    public FloatingBackpackItem(BackpackTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public BackpackTier tier() {
        return tier;
    }

    public static Properties baseProperties(BackpackTier tier) {
        return new Properties()
                .stacksTo(1)
                .component(ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty(tier.capacity()));
    }

    public static boolean isBackpack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FloatingBackpackItem;
    }

    public static BackpackTier tierOf(ItemStack stack) {
        if (stack.getItem() instanceof FloatingBackpackItem item) {
            return item.tier();
        }
        return null;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    /**
     * The backpack's contents live in a data component that is written again on every panel click and sync, and
     * {@code BackpackContents} holds item stacks (which compare by identity), so a held backpack never equals its
     * previous self. The default rule would replay the "item changed" dip of the first-person hand each time; only a
     * different item or a different hotbar slot should.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !isBackpack(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            return swapWithEquipmentSlot(this, level, player, hand);
        }
        if (level.isClientSide) {
            ClientBackpackActions.toggleFromUse();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
