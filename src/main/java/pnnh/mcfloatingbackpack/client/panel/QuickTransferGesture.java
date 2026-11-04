package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * State of one Ctrl+click / Ctrl+drag (011, extended by 012). Knows nothing about Minecraft: slots are
 * found through an injected {@link Resolver}, so the rules can be unit-tested.
 *
 * <ul>
 *   <li>the first slot's group fixes the direction for the whole drag: INVENTORY and CONTAINER slots
 *       both go <em>into the backpack</em>, PANEL slots go <em>out of it</em>;
 *   <li>a slot is reported at most once per drag (slots of different sides are different slots even if
 *       their indices are equal);
 *   <li>between two frames the mouse path is sampled every few pixels so a fast move does not skip slots.
 * </ul>
 */
public final class QuickTransferGesture {
    /**
     * Where a slot lives. INVENTORY = the player's own inventory slots (index 0-35), CONTAINER = slots of the
     * open container (index = menu slot index), PANEL = floating-panel slots (index = backpack slot).
     */
    public enum Side {
        INVENTORY,
        CONTAINER,
        PANEL
    }

    /** A slot under the cursor: its side, its index on that side, and whether it holds an item. */
    public record Hit(Side side, int index, boolean hasItem) {
        private long key() {
            return ((long) side.ordinal() << 32) | (index & 0xFFFFFFFFL);
        }
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
    private final Set<Long> visited = new HashSet<>();
    private double lastX;
    private double lastY;

    public boolean isActive() {
        return active;
    }

    /** The side of the slot the drag started on. */
    public Side side() {
        return side;
    }

    private static boolean intoBackpack(Side s) {
        return s != Side.PANEL;
    }

    /** Starts a drag on {@code start}; returns the slots to transfer right now (the start slot, if it holds an item). */
    public List<Hit> begin(Hit start, double x, double y) {
        active = true;
        side = start.side();
        visited.clear();
        visited.add(start.key());
        lastX = x;
        lastY = y;
        List<Hit> out = new ArrayList<>();
        if (start.hasItem()) {
            out.add(start);
        }
        return out;
    }

    /** Moves to (x, y); returns the slots newly passed over this step that hold an item and belong to the drag's group. */
    public List<Hit> advance(Resolver resolver, double x, double y) {
        List<Hit> out = new ArrayList<>();
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
            if (hit == null || intoBackpack(hit.side()) != intoBackpack(side)) {
                continue;
            }
            if (visited.add(hit.key()) && hit.hasItem()) {
                out.add(hit);
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
