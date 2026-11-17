package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 057: which backpacks changed since the last timed snapshot, and when the next round of timed snapshots is. {@link #mark} keeps the
 * latest contents of a changed backpack (a later change replaces it); {@link #due} hands out all of them once the time of the round has
 * come and starts the next round, so a backpack that did not change is never written. Only the server's main thread uses this. Pure
 * logic: {@code T} is whatever the caller keeps per backpack.
 */
public final class TimedWindow<T> {
    /** One backpack waiting for the round. */
    public record Due<T>(UUID uuid, T value) {
    }

    private final long intervalMs;
    private final Map<UUID, T> pending = new LinkedHashMap<>();
    private long nextAt;

    public TimedWindow(long intervalMs) {
        this.intervalMs = Math.max(1L, intervalMs);
    }

    public void mark(UUID uuid, T value, long now) {
        if (nextAt == 0) {
            nextAt = now + intervalMs;
        }
        pending.put(uuid, value);
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public int size() {
        return pending.size();
    }

    /** Takes out what changed since the last round if the round's time has come (and starts the next round); otherwise nothing. */
    public List<Due<T>> due(long now) {
        if (nextAt == 0 || now < nextAt) {
            return List.of();
        }
        nextAt = now + intervalMs;
        List<Due<T>> out = new ArrayList<>();
        for (Map.Entry<UUID, T> entry : pending.entrySet()) {
            out.add(new Due<>(entry.getKey(), entry.getValue()));
        }
        pending.clear();
        return out;
    }
}
