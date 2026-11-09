package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 030: which backpacks have changed and are waiting for a snapshot, and when each is due. A change is due when the backpack has
 * been quiet for {@link #QUIET_MS}, or when {@link #MAX_MS} have passed since its first change that is not written yet, so a
 * backpack that is used all the time is still written now and then. Marking a backpack again replaces what is waiting (only the
 * latest contents matter). Only the server's main thread uses this. Pure logic: {@code T} is whatever the caller keeps per
 * backpack (the contents, who used it, the capacity).
 */
public final class SnapshotScheduler<T> {
    public static final long QUIET_MS = 10_000L;
    public static final long MAX_MS = 5L * 60_000L;

    /** One waiting change, taken out of the scheduler. */
    public record Due<T>(UUID uuid, T value) {
    }

    private static final class Entry<T> {
        T value;
        final long first;
        long last;

        Entry(T value, long now) {
            this.value = value;
            this.first = now;
            this.last = now;
        }
    }

    private final Map<UUID, Entry<T>> pending = new LinkedHashMap<>();

    public void mark(UUID uuid, T value, long now) {
        Entry<T> entry = pending.get(uuid);
        if (entry == null) {
            pending.put(uuid, new Entry<>(value, now));
        } else {
            entry.value = value;
            entry.last = now;
        }
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public int size() {
        return pending.size();
    }

    /** Takes out (and returns) what is due at {@code now}. */
    public List<Due<T>> due(long now) {
        List<Due<T>> out = new ArrayList<>();
        Iterator<Map.Entry<UUID, Entry<T>>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry<T>> e = it.next();
            Entry<T> entry = e.getValue();
            if (now - entry.last >= QUIET_MS || now - entry.first >= MAX_MS) {
                out.add(new Due<>(e.getKey(), entry.value));
                it.remove();
            }
        }
        return out;
    }

    /** Takes out everything, due or not (a player leaving, the server stopping, a command about to read the files). */
    public List<Due<T>> drainAll() {
        return drainWhere(value -> true);
    }

    /** Takes out what {@code test} accepts (for example one player's backpacks). */
    public List<Due<T>> drainWhere(Predicate<T> test) {
        List<Due<T>> out = new ArrayList<>();
        Iterator<Map.Entry<UUID, Entry<T>>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry<T>> e = it.next();
            if (test.test(e.getValue().value)) {
                out.add(new Due<>(e.getKey(), e.getValue().value));
                it.remove();
            }
        }
        return out;
    }
}
