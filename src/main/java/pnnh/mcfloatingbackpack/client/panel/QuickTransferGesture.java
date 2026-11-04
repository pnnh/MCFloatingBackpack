package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * State of one Ctrl+click / Ctrl+drag (011). Knows nothing about Minecraft: slots are found through
 * an injected {@link Resolver}, so the rules can be unit-tested.
 *
 * <ul>
 *   <li>the side of the first slot fixes the direction for the whole drag;
 *   <li>a slot is reported at most once per drag;
 *   <li>between two frames the mouse path is sampled every few pixels so a fast move does not skip slots.
 * </ul>
 */
public final class QuickTransferGesture {
    /** Where a slot lives. Items go INVENTORY -> backpack, or PANEL -> inventory. */
    public enum Side {
        INVENTORY,
        PANEL
    }

    /** A slot under the cursor: its side, its index on that side, and whether it holds an item. */
    public record Hit(Side side, int index, boolean hasItem) {
    }

    @FunctionalInterface
    public interface Resolver {
        /** @return the slot at this GUI position, or null */
        Hit resolve(double x, double y);
    }

    /** Sampling step along the mouse path, in GUI pixels (a slot is 16 px, spaced 18 px). */
    static final double SAMPLE_STEP = 4.0;

    private boolean active;
    private Side side;
    private final Set<Integer> visited = new HashSet<>();
    private double lastX;
    private double lastY;

    public boolean isActive() {
        return active;
    }

    public Side side() {
        return side;
    }

    /** Starts a drag on {@code start}; returns the slots to transfer right now (the start slot, if it holds an item). */
    public List<Integer> begin(Hit start, double x, double y) {
        active = true;
        side = start.side();
        visited.clear();
        visited.add(start.index());
        lastX = x;
        lastY = y;
        List<Integer> out = new ArrayList<>();
        if (start.hasItem()) {
            out.add(start.index());
        }
        return out;
    }

    /** Moves to (x, y); returns the slots newly passed over this step that hold an item. */
    public List<Integer> advance(Resolver resolver, double x, double y) {
        List<Integer> out = new ArrayList<>();
        if (!active) {
            return out;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / SAMPLE_STEP));
        for (int k = 1; k <= steps; k++) {
            double px = lastX + dx * k / steps;
            double py = lastY + dy * k / steps;
            Hit hit = resolver.resolve(px, py);
            if (hit == null || hit.side() != side) {
                continue;
            }
            if (visited.add(hit.index()) && hit.hasItem()) {
                out.add(hit.index());
            }
        }
        lastX = x;
        lastY = y;
        return out;
    }

    public void stop() {
        active = false;
        side = null;
        visited.clear();
    }
}
