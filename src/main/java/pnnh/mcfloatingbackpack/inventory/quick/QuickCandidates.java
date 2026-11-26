package pnnh.mcfloatingbackpack.inventory.quick;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 067: which stacks the quick-access grid lists. The caller passes the main area in slot order: the tier's own cells, then each
 * visible extension row ({@link #ROW_CELLS} cells, left to right). Empty cells stay in the list as a blank id so the index is
 * the backpack slot. Tool areas, hidden rows and the backpack cursor are not in that list. Pure logic.
 */
public final class QuickCandidates {
    /** The built-in tag an item type needs to be listed. */
    public static final String TAG = "quick";

    /** Cells in one extension row. The caller lays visible rows out at this width; this class does not look at hidden rows. */
    public static final int ROW_CELLS = 9;

    private QuickCandidates() {
    }

    /**
     * @param baseCapacity the tier's own slot count (the cap is half of this, extension rows do not raise it)
     * @param slots item ids in main-area order; blank or null is an empty cell
     * @param tagged whether that item id carries {@link #TAG}
     * @return main-area slot indexes, in order, at most {@code baseCapacity / 2}
     */
    public static List<Integer> select(int baseCapacity, List<String> slots, Predicate<String> tagged) {
        int limit = baseCapacity / 2;
        if (slots == null || tagged == null || limit <= 0) {
            return List.of();
        }
        List<Integer> kept = new ArrayList<>();
        for (int i = 0; i < slots.size() && kept.size() < limit; i++) {
            String id = slots.get(i);
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (tagged.test(id)) {
                kept.add(i);
            }
        }
        return List.copyOf(kept);
    }
}
