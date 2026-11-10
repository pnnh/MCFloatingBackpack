package pnnh.mcfloatingbackpack.inventory.revision;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 040: the windows in which a player can confirm that they accept the risk of using a protected backpack: the first refusal opens a
 * window of {@link #WINDOW_MS} for that player and that backpack, and opening the same backpack again inside it confirms. A window is
 * only good for its own player and backpack, and it is gone once it is used, once it has run out, and when the player leaves.
 * Also a small limiter for the log line about an auto pickup that skipped a protected backpack. Time is always passed in by the
 * caller. Only the server's main thread calls this. Pure logic with no Minecraft types.
 */
public final class ProtectionWindows {
    public static final long WINDOW_MS = 60_000L;

    private record Key(UUID player, String backpack) {
    }

    private final Map<Key, Long> windows = new HashMap<>();
    private final Map<UUID, Long> lastPickupLog = new HashMap<>();

    /** Opens (or restarts) the window for this player and backpack. */
    public void open(UUID player, String backpack, long nowMs) {
        windows.put(new Key(player, backpack), nowMs + WINDOW_MS);
    }

    public boolean isOpen(UUID player, String backpack, long nowMs) {
        Long until = windows.get(new Key(player, backpack));
        return until != null && nowMs < until;
    }

    /** The window has been used. */
    public void consume(UUID player, String backpack) {
        windows.remove(new Key(player, backpack));
    }

    /** The player is gone: nothing they had opened or confirmed outlives them. */
    public void forgetPlayer(UUID player) {
        windows.keySet().removeIf(key -> key.player().equals(player));
        lastPickupLog.remove(player);
    }

    /** Whether the log line about a skipped auto pickup may be written now: at most one a minute for each player. */
    public boolean shouldLogPickupSkip(UUID player, long nowMs) {
        Long last = lastPickupLog.get(player);
        if (last != null && nowMs - last < WINDOW_MS) {
            return false;
        }
        lastPickupLog.put(player, nowMs);
        return true;
    }
}
