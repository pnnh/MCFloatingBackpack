package pnnh.mcfloatingbackpack.compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 059: lets the server tell a player once, for each screen they open, that it does not allow the panel to exchange items with that mod's
 * screens. It remembers, per player, the last (open screen, mod) it told them about; the same pair again says nothing. When the screen
 * is closed or the player leaves, the memory is dropped, so the next screen (even one with the same number) is told about again. Only
 * in memory. Pure logic.
 */
public final class DenialThrottle {
    private record Told(int containerId, String modId) {
    }

    private final Map<UUID, Told> told = new ConcurrentHashMap<>();

    /** @return true when the player should be told now (and is counted as told) */
    public boolean shouldNotify(UUID player, int containerId, String modId) {
        Told now = new Told(containerId, modId);
        return !now.equals(told.put(player, now));
    }

    public void forget(UUID player) {
        told.remove(player);
    }
}
