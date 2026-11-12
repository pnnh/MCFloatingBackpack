package pnnh.mcfloatingbackpack.pipe;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 049: "not again before so many ticks, for this key". Used twice: a player gets the same notice at most once a second, and a pipe in the
 * left hand-held button places at most once every few ticks (vanilla sends a start-of-mining packet every tick while the button is held, and
 * the server cannot tell a click from a hold). Pure logic, no Minecraft types.
 */
public final class Cooldown<K> {
    private final Map<K, Long> last = new HashMap<>();

    /**
     * Whether the key may go now: true (and the time is noted) if at least {@code ticks} ticks passed since it last did, or it never did;
     * otherwise false and nothing changes, so a steady stream of tries does not keep pushing the end of the cooldown away.
     */
    public boolean tryAcquire(K key, long nowTick, int ticks) {
        Long before = last.get(key);
        if (before != null && nowTick - before < ticks && nowTick >= before) {
            return false;
        }
        last.put(key, nowTick);
        return true;
    }

    /** Forgets the keys that match (a player who logged out). */
    public void forget(Predicate<K> which) {
        last.keySet().removeIf(which);
    }

    public int size() {
        return last.size();
    }
}
