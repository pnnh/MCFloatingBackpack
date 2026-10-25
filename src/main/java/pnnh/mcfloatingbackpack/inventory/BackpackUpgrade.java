package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Resizes a slot list when a backpack is upgraded. Prefix order is kept.
 */
public final class BackpackUpgrade {
    private BackpackUpgrade() {
    }

    public static <T> List<T> fit(List<T> items, int capacity, T empty) {
        int size = Math.max(0, capacity);
        List<T> fitted = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            T value = items != null && i < items.size() ? items.get(i) : null;
            fitted.add(value == null ? empty : value);
        }
        return fitted;
    }
}
