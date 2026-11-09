package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 026: keeps the same message from being sent to the same player over and over (a refused attempt repeated every tick would
 * fill the chat): one per player and reason in {@link #WINDOW_MS}. Time is passed in by the caller. Pure logic.
 */
public final class NoticeLimiter {
    public static final long WINDOW_MS = 30_000L;

    public enum Reason {
        /** The backpack was deleted by an administrator. */
        TOMBSTONE
    }

    private final Map<UUID, Map<Reason, Long>> last = new HashMap<>();

    /** @return true if the message may be sent now (and the window starts again) */
    public boolean shouldNotify(UUID player, Reason reason, long now) {
        Map<Reason, Long> byReason = last.computeIfAbsent(player, p -> new EnumMap<>(Reason.class));
        Long before = byReason.get(reason);
        if (before != null && now - before < WINDOW_MS) {
            return false;
        }
        byReason.put(reason, now);
        return true;
    }
}
