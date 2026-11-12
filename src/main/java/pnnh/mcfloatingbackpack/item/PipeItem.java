package pnnh.mcfloatingbackpack.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * The milk pipe on an adult cow (a mooshroom is one) or goat: milks it. Sneaking is left to {@link #use} (which fills a container). The client only
     * says "that worked" so that the arm swings; the server does it.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (kind != PipeKind.MILK || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()
                || !(target instanceof Cow || target instanceof Goat) || target.isBaby()) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return PipeActions.absorbEntity(serverPlayer, kind, target) == PipeActions.Result.DONE
                ? InteractionResult.sidedSuccess(false)
                : InteractionResult.FAIL;
    }

    /**
     * The honey pipe on a beehive or bee nest whose honey is ready: takes the honey. The hive's own item interaction passes for a pipe, so this is
     * reached; sneaking is left to {@link #use}.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || kind != PipeKind.HONEY || context.getHand() != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BeehiveBlock) || state.getValue(BeehiveBlock.HONEY_LEVEL) < PipeActions.READY_HONEY_LEVEL) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        Direction face = context.getClickedFace();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(face), face, context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        return PipeActions.absorbHive(serverPlayer, kind, pos, state) == PipeActions.Result.DONE
                ? InteractionResult.sidedSuccess(false)
                : InteractionResult.FAIL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // the server does it all; the client only shows what the server's answer makes it show. Sneaking is kept for 050.
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        // 050: sneaking fills an empty container from the tank, whatever is pointed at (the block's and the entity's own interactions pass first)
        if (player.isShiftKeyDown()) {
            return PipeActions.discharge(serverPlayer, kind) == PipeActions.Result.DONE
                    ? InteractionResultHolder.success(stack)
                    : InteractionResultHolder.fail(stack);
        }
        // milk and honey are taken from a cow and a beehive (see interactLivingEntity and useOn), not from a source block
        if (!kind.hasBlockForm()) {
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
        // 050: a milk or honey pipe is used on a cow or a beehive; the others on a source block and, to pour out, on a block
        switch (kind) {
            case MILK -> tooltip.add(Component.translatable("item.mcfloatingbackpack.pipe.milk.tooltip").withStyle(ChatFormatting.GRAY));
            case HONEY -> tooltip.add(Component.translatable("item.mcfloatingbackpack.pipe.honey.tooltip").withStyle(ChatFormatting.GRAY));
            default -> tooltip.add(Component.translatable("item.mcfloatingbackpack.pipe.tooltip").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(kind == PipeKind.HONEY
                        ? "item.mcfloatingbackpack.pipe.discharge_bottle"
                        : "item.mcfloatingbackpack.pipe.discharge")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mcfloatingbackpack.pipe.needs_tank").withStyle(ChatFormatting.GRAY));
    }
}
