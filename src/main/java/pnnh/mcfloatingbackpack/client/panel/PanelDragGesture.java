package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic.DragMode;
import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic.StackOps;

/**
 * State of one press-and-drag on the floating panel's slots (013). Knows nothing about Minecraft:
 * slots and the cursor are read through injected accessors and time is passed in, so the rules can be
 * unit-tested.
 *
 * <ul>
 *   <li><b>Distributing</b>: pressed with an item on the cursor. Nothing is sent until the button is
 *       released; the slots passed over that are valid for a drag are collected, and the release decides
 *       between a distribution (2+ slots), an ordinary click (1 slot, or none) or a double-click gather.
 *   <li><b>Gathering</b>: pressed on an item with an empty cursor (the ordinary pickup happens at once).
 *       While the button stays down, every new slot holding the same kind of item is reported so the
 *       server can pull it into the cursor.
 * </ul>
 */
public final class PanelDragGesture<T> {
    public enum State {
        IDLE,
        DISTRIBUTING,
        GATHERING
    }

    public enum Kind {
        NONE,
        CLICK,
        DISTRIBUTE,
        GATHER_ALL
    }

    /** What to do when the button comes up. {@code slots} depends on the kind; {@code right} is the button used. */
    public record Release(Kind kind, boolean right, List<Integer> slots) {
    }

    /** What a distribution would do right now, for the on-screen preview. */
    public record Preview<T>(List<Integer> slots, List<T> stacks, List<Boolean> capped, T cursorLeft) {
    }

    @FunctionalInterface
    public interface Resolver {
        /** @return the panel slot at this GUI position, or -1 */
        int slotAt(double x, double y);
    }

    @FunctionalInterface
    public interface SlotStacks<T> {
        T get(int slotIndex);
    }

    /** Sampling step along the mouse path, in GUI pixels (a slot is 18 px apart). */
    static final double SAMPLE_STEP = 4.0;
    /** Vanilla's double-click window. */
    public static final long DOUBLE_CLICK_MS = 250L;

    private final StackOps<T> ops;
    private final int slotLimit;

    private State state = State.IDLE;
    private boolean right;
    private int pressSlot = -1;
    private boolean doubleClick;
    private T gatherKind;
    private final List<Integer> selected = new ArrayList<>();
    private final Set<Integer> visited = new HashSet<>();
    private double lastX;
    private double lastY;

    private int lastClickSlot = -1;
    private long lastClickTime;
    private boolean lastClickRight;

    public PanelDragGesture(StackOps<T> ops, int slotLimit) {
        this.ops = ops;
        this.slotLimit = slotLimit;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state != State.IDLE;
    }

    public List<Integer> selectedSlots() {
        return selected;
    }

    /**
     * A press on a panel slot (Ctrl/Shift already ruled out by the caller).
     *
     * @return true when the gesture takes over the press (cursor holds an item: the click is deferred to the
     *     release); false when the caller should send the ordinary click right away (empty cursor)
     */
    public boolean beginPress(int slot, boolean rightButton, T cursor, SlotStacks<T> stacks, double x, double y, long nowMs) {
        boolean dbl = slot == lastClickSlot && nowMs - lastClickTime < DOUBLE_CLICK_MS && rightButton == lastClickRight;
        lastClickSlot = slot;
        lastClickTime = nowMs;
        lastClickRight = rightButton;

        clear();
        right = rightButton;
        pressSlot = slot;
        lastX = x;
        lastY = y;
        visited.add(slot);
        if (!ops.isEmpty(cursor)) {
            state = State.DISTRIBUTING;
            doubleClick = dbl && !rightButton;
            if (PanelCursorLogic.isValidDragSlot(ops, stacks.get(slot), cursor, 0)) {
                selected.add(slot);
            }
            return true;
        }
        T under = stacks.get(slot);
        if (!rightButton && !ops.isEmpty(under)) {
            state = State.GATHERING;
            gatherKind = under;
        }
        return false;
    }

    /**
     * Moves to (x, y). While distributing, newly passed slots that are valid for the drag are selected.
     * While gathering, the newly passed slots holding the gathered kind are returned.
     */
    public List<Integer> advance(Resolver resolver, SlotStacks<T> stacks, T cursor, double x, double y) {
        List<Integer> gathered = new ArrayList<>();
        if (state == State.IDLE) {
            return gathered;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / SAMPLE_STEP));
        for (int k = 1; k <= steps; k++) {
            int slot = resolver.slotAt(lastX + dx * k / steps, lastY + dy * k / steps);
            if (slot < 0 || !visited.add(slot)) {
                continue;
            }
            T stack = stacks.get(slot);
            if (state == State.DISTRIBUTING) {
                if (PanelCursorLogic.isValidDragSlot(ops, stack, cursor, selected.size())) {
                    selected.add(slot);
                }
            } else if (!ops.isEmpty(stack) && ops.sameKind(stack, gatherKind)) {
                gathered.add(slot);
            }
        }
        lastX = x;
        lastY = y;
        return gathered;
    }

    /** The button came up over {@code slotUnderCursor} (-1 if none); ends the gesture. */
    public Release release(int slotUnderCursor) {
        Release result = new Release(Kind.NONE, right, List.of());
        if (state == State.DISTRIBUTING) {
            if (doubleClick && slotUnderCursor == pressSlot) {
                result = new Release(Kind.GATHER_ALL, false, List.of(pressSlot));
            } else if (selected.size() >= 2) {
                result = new Release(Kind.DISTRIBUTE, right, List.copyOf(selected));
            } else if (selected.size() == 1) {
                result = new Release(Kind.CLICK, right, List.of(selected.get(0)));
            } else if (slotUnderCursor >= 0) {
                result = new Release(Kind.CLICK, right, List.of(slotUnderCursor));
            }
        }
        clear();
        return result;
    }

    /** Drops the gesture without any request (what was already gathered stays gathered). */
    public void cancel() {
        clear();
    }

    /** What the drag would do if released now; null unless 2+ slots are selected (vanilla shows no preview for one). */
    public Preview<T> preview(T cursor, SlotStacks<T> stacks) {
        if (state != State.DISTRIBUTING || selected.size() < 2 || ops.isEmpty(cursor)) {
            return null;
        }
        List<T> current = new ArrayList<>(selected.size());
        for (int slot : selected) {
            current.add(stacks.get(slot));
        }
        PanelCursorLogic.Distribution<T> d =
                PanelCursorLogic.distribute(ops, right ? DragMode.RIGHT : DragMode.LEFT, cursor, current, slotLimit);
        return new Preview<>(List.copyOf(selected), d.stacks(), d.capped(), d.cursorLeft());
    }

    private void clear() {
        state = State.IDLE;
        pressSlot = -1;
        doubleClick = false;
        gatherKind = null;
        selected.clear();
        visited.clear();
    }
}
