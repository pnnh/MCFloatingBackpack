package pnnh.mcfloatingbackpack.diag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers, for one player, where each backpack (by identity) was the last time it was looked at, and says which have gone
 * since. A backpack that is gone is not always lost: it may have been dropped, put into a container or carried on the cursor,
 * which the caller finds out for the ones this reports. Generic in the identity so it can be tested with strings. Pure logic.
 */
public final class BackpackWatch<K> {
    /** A backpack that was seen before and is not any more, and where it was. */
    public record Gone<K>(K id, String lastSeenAt) {
    }

    private Map<K, String> last = new HashMap<>();

    /**
     * @param now where every backpack is now (identity to a description of its place)
     * @return the backpacks that were there at the previous call and are not now, in no particular order
     */
    public List<Gone<K>> update(Map<K, String> now) {
        List<Gone<K>> gone = new ArrayList<>();
        for (Map.Entry<K, String> entry : last.entrySet()) {
            if (!now.containsKey(entry.getKey())) {
                gone.add(new Gone<>(entry.getKey(), entry.getValue()));
            }
        }
        last = new HashMap<>(now);
        return gone;
    }

    /** Forgets everything (the player logged out or changed world). */
    public void reset() {
        last = new HashMap<>();
    }
}
