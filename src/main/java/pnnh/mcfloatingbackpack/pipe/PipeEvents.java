package pnnh.mcfloatingbackpack.pipe;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import pnnh.mcfloatingbackpack.item.PipeItem;

/**
 * 049: the left button with a pipe in the main hand. It never mines (the event is cancelled on both sides: the server does not break the block,
 * the client neither predicts it nor starts mining, and a creative player does not smash it); on the server the start of the click pours a bucket
 * out of the tank. While the button is held vanilla sends the start every tick, so pouring is held back to once every few ticks ({@link PipeCooldowns}).
 */
public final class PipeEvents {
    private PipeEvents() {
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        PipeKind pipe = PipeItem.kindOf(player.getMainHandItem());
        if (pipe == null) {
            return;
        }
        event.setCanceled(true);
        if (event.getLevel().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        long now = serverPlayer.getServer() == null ? serverPlayer.level().getGameTime() : serverPlayer.getServer().getTickCount();
        if (!PipeCooldowns.placementMayGo(serverPlayer.getUUID(), now)) {
            return;
        }
        Direction face = event.getFace() == null ? Direction.UP : event.getFace();
        PipeActions.place(serverPlayer, pipe, event.getPos(), face);
    }
}
