package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules behind the floating panel's drag gestures (013), copied from vanilla's
 * {@code AbstractContainerMenu} (drag-distribute = QUICK_CRAFT, gather = PICKUP_ALL) so the panel behaves
 * like the real inventory. Over abstract stacks, with no Minecraft dependency: the unit tests drive the
 * same code the server and the client preview run.
 *
 * <p>A vanilla quirk is kept on purpose: a same-kind stack that is already full still counts as a valid slot
 * for a drag (it takes a share of the split and gains nothing).
 */
public final class PanelCursorLogic {
    private PanelCursorLogic() {
    }

    public enum DragMode {
        LEFT,
        RIGHT
    }

    /** Vanilla {@code canItemQuickReplace(slot, stack, true)}: empty, or the same kind of stack. */
    public static <T> boolean canQuickReplace(QuickTransferLogic.StackOps<T> ops, T slot, T cursor) {
        if (ops.isEmpty(slot)) {
            return true;
        }
        return ops.sameKind(slot, cursor) && ops.count(slot) <= ops.maxStack(cursor);
    }

    /** Whether a slot may join a drag that already has {@code selectedCount} slots (panel slots accept any item). */
    public static <T> boolean isValidDragSlot(QuickTransferLogic.StackOps<T> ops, T slot, T cursor, int selectedCount) {
        return canQuickReplace(ops, slot, cursor) && ops.count(cursor) > selectedCount;
    }

    /** Vanilla {@code getQuickCraftPlaceCount}: left button splits evenly (floor), right button places one. */
    public static int placeCount(DragMode mode, int cursorCount, int slotsCount) {
        return mode == DragMode.LEFT ? Math.floorDiv(cursorCount, Math.max(1, slotsCount)) : 1;
    }

    /**
     * @param stacks the new contents of each selected slot, in the order given
     * @param capped for each slot, whether the share was cut down to the slot's limit (vanilla shows it in yellow)
     * @param rejected nothing could be placed (for example the cursor holds fewer items than there are slots)
     */
    public record Distribution<T>(List<T> stacks, T cursorLeft, List<Boolean> capped, boolean rejected) {
    }

    /**
     * Settles a drag over {@code slotStacks} (the selected slots' current contents). Each slot is re-checked
     * like vanilla does when the drag ends: replaceable, and the cursor must hold at least as many items as
     * there are slots.
     */
    public static <T> Distribution<T> distribute(
            QuickTransferLogic.StackOps<T> ops, DragMode mode, T cursor, List<T> slotStacks, int slotLimit) {
        int n = slotStacks.size();
        int cursorCount = ops.count(cursor);
        List<T> out = new ArrayList<>(n);
        List<Boolean> capped = new ArrayList<>(n);
        int remaining = cursorCount;
        boolean placedAny = false;
        for (T current : slotStacks) {
            if (n > 0 && canQuickReplace(ops, current, cursor) && cursorCount >= n) {
                int existing = ops.isEmpty(current) ? 0 : ops.count(current);
                int limit = Math.min(ops.maxStack(cursor), slotLimit);
                int wanted = placeCount(mode, cursorCount, n) + existing;
                int placed = Math.min(wanted, limit);
                remaining -= placed - existing;
                out.add(ops.withCount(cursor, placed));
                capped.add(wanted > limit);
                placedAny = true;
            } else {
                out.add(current);
                capped.add(false);
            }
        }
        return new Distribution<>(out, ops.withCount(cursor, remaining), capped, !placedAny);
    }

    public record Gathered<T>(T slot, T cursor, int moved) {
    }

    /** Takes from one slot into the cursor: same kind only, up to the cursor's max stack; the rest stays in the slot. */
    public static <T> Gathered<T> gather(QuickTransferLogic.StackOps<T> ops, T slot, T cursor) {
        if (ops.isEmpty(slot) || ops.isEmpty(cursor) || !ops.sameKind(slot, cursor)) {
            return new Gathered<>(slot, cursor, 0);
        }
        int space = ops.maxStack(cursor) - ops.count(cursor);
        int moved = Math.min(ops.count(slot), Math.max(space, 0));
        if (moved <= 0) {
            return new Gathered<>(slot, cursor, 0);
        }
        return new Gathered<>(
                ops.withCount(slot, ops.count(slot) - moved), ops.withCount(cursor, ops.count(cursor) + moved), moved);
    }

    public record PickedAll<T>(List<T> slots, T cursor, int moved) {
    }

    /**
     * Vanilla {@code PICKUP_ALL}: gather every same-kind stack into the cursor, partial stacks first, then
     * the full ones, until the cursor is full.
     */
    public static <T> PickedAll<T> pickupAll(QuickTransferLogic.StackOps<T> ops, T cursor, List<T> slots) {
        List<T> out = new ArrayList<>(slots);
        T current = cursor;
        int moved = 0;
        if (!ops.isEmpty(cursor)) {
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < out.size() && ops.count(current) < ops.maxStack(current); i++) {
                    T slot = out.get(i);
                    if (ops.isEmpty(slot) || !ops.sameKind(slot, current)) {
                        continue;
                    }
                    if (pass == 0 && ops.count(slot) == ops.maxStack(slot)) {
                        continue;
                    }
                    Gathered<T> g = gather(ops, slot, current);
                    out.set(i, g.slot());
                    current = g.cursor();
                    moved += g.moved();
                }
            }
        }
        return new PickedAll<>(out, current, moved);
    }
}
