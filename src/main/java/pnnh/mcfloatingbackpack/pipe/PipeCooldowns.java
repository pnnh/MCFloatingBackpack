package pnnh.mcfloatingbackpack.pipe;

import java.util.UUID;

/**
 * 049: the two cooldowns the pipes keep, one place so a player who logs out is forgotten in both. Notices (a player gets the same one at most
 * once a second) and the left button (a held button places at most once every few ticks) each have their own clock; time is the server's tick count.
 */
public final class PipeCooldowns {
    /** One notice for one player. */
    public record NoticeKey(UUID player, PipePlanner.Notice notice) {
    }

    /** Same notice to the same player: not within this many ticks. */
    public static final int NOTICE_TICKS = 20;
    /** Two placements by the same player: not within this many ticks (vanilla sends a start-of-mining packet every tick while the button is held). */
    public static final int PLACE_TICKS = 4;

    private static final Cooldown<NoticeKey> NOTICES = new Cooldown<>();
    private static final Cooldown<UUID> PLACEMENTS = new Cooldown<>();

    private PipeCooldowns() {
    }

    public static boolean noticeMayGo(UUID player, PipePlanner.Notice notice, long nowTick) {
        return NOTICES.tryAcquire(new NoticeKey(player, notice), nowTick, NOTICE_TICKS);
    }

    public static boolean placementMayGo(UUID player, long nowTick) {
        return PLACEMENTS.tryAcquire(player, nowTick, PLACE_TICKS);
    }

    public static void forget(UUID player) {
        NOTICES.forget(key -> key.player().equals(player));
        PLACEMENTS.forget(player::equals);
    }
}
