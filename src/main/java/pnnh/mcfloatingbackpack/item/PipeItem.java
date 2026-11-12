package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import pnnh.mcfloatingbackpack.pipe.PipeActions;
import pnnh.mcfloatingbackpack.pipe.PipeKind;

/**
 * 049: a pipe. An ordinary item with no data: held in the main hand it takes in a bucket of its liquid from a source block (right click) into
 * the matching tank of the backpack the B key would open, and pours a bucket out of that tank (left click, see {@code PipeEvents}).
 */
public class PipeItem extends Item {
    private final PipeKind kind;

    public PipeItem(PipeKind kind, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public PipeKind kind() {
        return kind;
    }

    /** The pipe a stack is, or {@code null} for any other stack. */
    public static PipeKind kindOf(ItemStack stack) {
        return stack.getItem() instanceof PipeItem item ? item.kind : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // the server does it all; the client only shows what the server's answer makes it show. Sneaking is kept for 050.
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        // like an empty bucket: look through the water to its surface (only source blocks), so a lake bed under it does not get in the way
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(face), face, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return switch (PipeActions.absorb(serverPlayer, kind, hit)) {
            case NOT_FOR_THIS_PIPE -> InteractionResultHolder.pass(stack);
            case REFUSED -> InteractionResultHolder.fail(stack);
            case DONE -> InteractionResultHolder.success(stack);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mcfloatingbackpack.pipe.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mcfloatingbackpack.pipe.needs_tank").withStyle(ChatFormatting.GRAY));
    }
}
