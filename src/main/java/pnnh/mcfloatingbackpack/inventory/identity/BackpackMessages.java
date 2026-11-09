package pnnh.mcfloatingbackpack.inventory.identity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import pnnh.mcfloatingbackpack.inventory.identity.NoticeLimiter.Reason;

/**
 * 026: what the player is told when the registry refuses or changes something. The same message to the same player is sent at
 * most once in {@link NoticeLimiter#WINDOW_MS}. Only the server's main thread calls this.
 */
final class BackpackMessages {
    private static final NoticeLimiter LIMITER = new NoticeLimiter();

    private BackpackMessages() {
    }

    /** Chat only. */
    static void chat(ServerPlayer player, Reason reason, Component text) {
        if (LIMITER.shouldNotify(player.getUUID(), reason, System.currentTimeMillis())) {
            player.displayClientMessage(text, false);
        }
    }

    /** Chat, and the same text over the hotbar (the player just tried to open something and nothing happened). */
    static void chatAndActionBar(ServerPlayer player, Reason reason, Component text) {
        if (LIMITER.shouldNotify(player.getUUID(), reason, System.currentTimeMillis())) {
            player.displayClientMessage(text, false);
            player.displayClientMessage(text, true);
        }
    }
}
