package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Resolver;

/**
 * State of one Shift-select press on the floating panel's slots (014). Knows nothing about Minecraft: the
 * slot under a point comes from an injected resolver, so the rules can be unit-tested.
 *
 * <p>Pressing only starts the gesture; the selection changes later. Releasing without having left the start
 * slot is a click and toggles that slot. Moving on to another slot makes it a drag: the start slot and every
 * slot passed over are added, and a drag never removes anything.
 */
public final class SelectionGesture {
    public enum State {
        IDLE,
        SELECTING,
        /** Ctrl pressed on a selected slot: the whole selection moves out when the button comes up. */
        BATCH_PENDING
    }

    /** Sampling step along the mouse path, in GUI pixels (a slot is 18 px apart). Same as the other panel gestures. */
    static final double SAMPLE_STEP = 4.0;

    private State state = State.IDLE;
    private int startSlot = -1;
    private boolean dragged;
    private final Set<Integer> visited = new HashSet<>();
    private double lastX;
    private double lastY;

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    public void beginSelect(int slot, double x, double y) {
        clear();
        state = State.SELECTING;
        startSlot = slot;
        visited.add(slot);
        lastX = x;
        lastY = y;
    }

    /** Moves to (x, y); slots newly passed over join the selection once the gesture is a drag. */
    public void advance(Resolver resolver, PanelSelection selection, double x, double y) {
        if (state != State.SELECTING) {
            return;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / SAMPLE_STEP));
        List<Integer> added = new ArrayList<>();
        for (int k = 1; k <= steps; k++) {
            int slot = resolver.slotAt(lastX + dx * k / steps, lastY + dy * k / steps);
            if (slot < 0 || !visited.add(slot)) {
                continue;
            }
            if (!dragged) {
                dragged = true;
                added.add(startSlot);
            }
            added.add(slot);
        }
        selection.addAll(added);
        lastX = x;
        lastY = y;
    }

    /** The button came up: a click toggles the start slot, a drag has already done its work. */
    public void release(PanelSelection selection) {
        if (state == State.SELECTING && !dragged) {
            selection.toggle(startSlot);
        }
        clear();
    }

    /**
     * Ctrl pressed on {@code slot}: starts a batch only if the slot is selected and the cursor is empty.
     * Nothing is sent now; moving the mouse changes nothing.
     */
    public boolean beginBatch(int slot, PanelSelection selection, boolean cursorEmpty) {
        if (!cursorEmpty || !selection.contains(slot)) {
            return false;
        }
        clear();
        state = State.BATCH_PENDING;
        return true;
    }

    /**
     * The button came up. @return the selected slots that currently hold an item, ascending, when everything
     * still holds (Ctrl still down, no Shift, panel visible, same screen, empty cursor); otherwise nothing.
     * The selection is never changed.
     */
    public int[] releaseBatch(boolean ctrlHeld, boolean shiftHeld, boolean visible, boolean sameScreen,
            boolean cursorEmpty, PanelSelection selection, IntPredicate hasItem) {
        boolean pending = state == State.BATCH_PENDING;
        clear();
        if (!pending || !ctrlHeld || shiftHeld || !visible || !sameScreen || !cursorEmpty) {
            return new int[0];
        }
        return java.util.Arrays.stream(selection.snapshot()).filter(hasItem).toArray();
    }

    /** Drops the gesture; slots a drag already added stay, an unsettled click is forgotten. */
    public void cancel() {
        clear();
    }

    private void clear() {
        state = State.IDLE;
        startSlot = -1;
        dragged = false;
        visited.clear();
    }
}
