package pnnh.mcfloatingbackpack.client.panel;

import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The floating panel's selected slots (014): backpack slot indexes, kept ascending and without repeats.
 * Client-only UI state; the server never sees it. It knows nothing about slot contents (empty slots can be
 * selected), screen positions or scrolling, so later features can read it without touching its rules.
 *
 * <p>Kept free of Minecraft types so it can be unit-tested.
 */
public final class PanelSelection {
    private static final PanelSelection INSTANCE = new PanelSelection();

    private final TreeSet<Integer> slots = new TreeSet<>();
    /** Indexes at or above this are not real slots; unbounded until the first {@link #syncContext}. */
    private int slotCount = Integer.MAX_VALUE;
    private boolean hasContext;
    private Object refKey;

    PanelSelection() {
    }

    public static PanelSelection get() {
        return INSTANCE;
    }

    /** @return true if the slot is selected afterwards */
    public boolean toggle(int slot) {
        if (slots.remove(slot)) {
            return false;
        }
        return inRange(slot) && slots.add(slot);
    }

    public void addAll(Collection<Integer> toAdd) {
        for (int slot : toAdd) {
            if (inRange(slot)) {
                slots.add(slot);
            }
        }
    }

    public void clear() {
        slots.clear();
    }

    public boolean contains(int slot) {
        return slots.contains(slot);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** The selected slots, ascending; a copy. */
    public int[] snapshot() {
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Called once per client tick. The selection is cleared when the panel is not visible, or when the
     * backpack it belongs to ({@code refKey}) or its capacity changed: indexes of one backpack mean nothing
     * in another. Scrolling and resizing never reach this method, because the selection is by slot index.
     */
    public void syncContext(boolean visible, Object newRefKey, int newSlotCount) {
        if (!visible) {
            slots.clear();
            hasContext = false;
            refKey = null;
            slotCount = Integer.MAX_VALUE;
            return;
        }
        if (hasContext && (!Objects.equals(refKey, newRefKey) || slotCount != newSlotCount)) {
            slots.clear();
        }
        hasContext = true;
        refKey = newRefKey;
        slotCount = newSlotCount;
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < slotCount;
    }
}
