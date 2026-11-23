package pnnh.mcfloatingbackpack.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 062: which of the backpacks a player's saved data shows in Accessories slots that no longer exist are to be given back. Accessories drops
 * what lies in a slot type that is gone (it does not hand it back), so the mod gives such a backpack back itself, once, when the player
 * logs in. The one failure to avoid is making a second backpack out of one: a backpack with a number (its identity) is given back only if
 * the player does not have that number now; one without a number (never opened) cannot be told apart from another of the same tier, so
 * they are counted: as many are given back as are missing from what the saved data showed, and never more than lay in the vanished
 * slots. Pure logic over plain values, so it is tested without the game.
 */
public final class OrphanRules {
    private OrphanRules() {
    }

    /** A backpack found in the saved data: the Accessories slot type it lay in, its item, and its number (null: it has none). */
    public record Entry(String slotName, String itemId, UUID identity) {
    }

    /**
     * @param entries every backpack the saved Accessories data shows, in any slot type
     * @param validSlotTypes the slot types Accessories gives this player now
     * @param presentIdentities the numbers of the backpacks the player has now (inventory, cursor, Accessories slots, lying near)
     * @param fileUnidentified per item id, how many backpacks without a number the saved data showed (inventory and every Accessories slot)
     * @param liveUnidentified per item id, how many the player has now, counted over the same places
     * @return the positions in {@code entries} of the backpacks to give back, in order, each at most once
     */
    public static List<Integer> decide(
            List<Entry> entries,
            Set<String> validSlotTypes,
            Set<UUID> presentIdentities,
            Map<String, Integer> fileUnidentified,
            Map<String, Integer> liveUnidentified) {
        List<Integer> result = new ArrayList<>();
        Map<String, List<Integer>> unidentifiedByItem = new LinkedHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (validSlotTypes.contains(entry.slotName())) {
                continue;
            }
            if (entry.identity() != null) {
                if (!presentIdentities.contains(entry.identity())) {
                    result.add(i);
                }
            } else {
                unidentifiedByItem.computeIfAbsent(entry.itemId(), k -> new ArrayList<>()).add(i);
            }
        }
        Map<String, Integer> missing = new HashMap<>();
        for (Map.Entry<String, List<Integer>> item : unidentifiedByItem.entrySet()) {
            int shown = fileUnidentified.getOrDefault(item.getKey(), 0);
            int now = liveUnidentified.getOrDefault(item.getKey(), 0);
            missing.put(item.getKey(), Math.max(0, shown - now));
            List<Integer> positions = item.getValue();
            int give = Math.min(missing.get(item.getKey()), positions.size());
            result.addAll(positions.subList(0, give));
        }
        result.sort(Integer::compare);
        return result;
    }
}
