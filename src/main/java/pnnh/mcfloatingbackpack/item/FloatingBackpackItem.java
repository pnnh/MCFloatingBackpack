package pnnh.mcfloatingbackpack.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import pnnh.mcfloatingbackpack.client.ClientBackpackActions;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;

/**
 * Leather floating backpack: chest-equippable, opens overlay via use / hotkey.
 */
public class FloatingBackpackItem extends ArmorItem {
    public FloatingBackpackItem(Properties properties) {
        super(ArmorMaterials.LEATHER, Type.CHESTPLATE, properties);
    }

    public static Properties baseProperties() {
        return new Properties()
                .stacksTo(1)
                .component(ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty());
    }

    public static boolean isBackpack(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FloatingBackpackItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !isBackpack(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        // Prefer equip-on-use vanilla armor behavior when sneaking? Spec: right-click toggles panel.
        // Shift-right-click keeps vanilla armor equip.
        if (player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        if (level.isClientSide) {
            ClientBackpackActions.toggleFromUse();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
