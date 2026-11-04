package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import pnnh.mcfloatingbackpack.item.BackpackTier;

/**
 * How many slots each area has, derived from the backpack's main capacity (016). The tool areas follow the
 * tier's default size, so a tier whose default size changes later changes them too; the size the panel is
 * currently shrunk to never matters. Free of Minecraft types so it can be unit-tested.
 */
public final class ToolAreaCapacity {
    private ToolAreaCapacity() {
    }

    /**
     * Left/right: the default row count. Bottom: the default column count plus the two side columns, because
     * the bottom row runs under the whole panel width.
     */
    public static int of(PanelArea area, int mainCapacity) {
        return switch (area) {
            case MAIN -> mainCapacity;
            case LEFT, RIGHT -> Math.max(1, (mainCapacity + BackpackTier.COLUMNS - 1) / BackpackTier.COLUMNS);
            case BOTTOM -> BackpackTier.COLUMNS + 2;
        };
    }

    /**
     * Resizes a stored tool-area list to the area's capacity. Items are never dropped: when the capacity got
     * smaller the list is only cut after the last slot that still holds something, so those slots stay
     * visible and can be emptied by hand.
     */
    public static <T> List<T> fit(List<T> stored, PanelArea area, int mainCapacity, T empty) {
        return fit(stored, area, mainCapacity, empty, value -> value.equals(empty));
    }

    /** Same, for element types without a usable {@code equals} (item stacks): {@code isEmpty} says what an empty slot is. */
    public static <T> List<T> fit(
            List<T> stored, PanelArea area, int mainCapacity, T empty, Predicate<T> isEmpty) {
        int capacity = of(area, mainCapacity);
        int stop = stored == null ? 0 : stored.size();
        int lastUsed = -1;
        for (int i = 0; i < stop; i++) {
            T value = stored.get(i);
            if (value != null && !isEmpty.test(value)) {
                lastUsed = i;
            }
        }
        int size = Math.max(capacity, lastUsed + 1);
        List<T> fitted = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            T value = stored != null && i < stored.size() ? stored.get(i) : null;
            fitted.add(value == null ? empty : value);
        }
        return fitted;
    }
}
