package pnnh.mcfloatingbackpack.inventory.trash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;
import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic.StackOps;

/**
 * The rules of the trash can's list (021): the newest group first, at most {@link #MAX_GROUPS} groups, the oldest one
 * destroyed when a tenth arrives, groups never merged. Written over an abstract stack type like
 * {@link QuickTransferLogic}, so the code that runs in game is the code under test. Every method returns new lists and
 * leaves its inputs alone.
 */
public final class TrashBin {
    public static final int MAX_GROUPS = 9;

    private TrashBin() {
    }

    /** The list after a push and the group that did not fit any more (empty when nothing was destroyed). */
    public record Push<T>(List<T> groups, T destroyed) {
    }

    /** The list after a take and the group that was taken (empty when the index named no group). */
    public record Take<T>(List<T> groups, T taken) {
    }

    /** Puts {@code stack} first; if that makes {@link #MAX_GROUPS} + 1 groups the last one is dropped and reported. */
    public static <T> Push<T> push(StackOps<T> ops, List<T> groups, T stack) {
        List<T> next = new ArrayList<>(groups == null ? List.of() : groups);
        if (stack == null || ops.isEmpty(stack)) {
            return new Push<>(next, ops.empty());
        }
        next.add(0, stack);
        T destroyed = ops.empty();
        if (next.size() > MAX_GROUPS) {
            destroyed = next.remove(next.size() - 1);
        }
        return new Push<>(next, destroyed);
    }

    /** Removes the group at {@code index}; the ones behind it move forward. */
    public static <T> Take<T> take(StackOps<T> ops, List<T> groups, int index) {
        List<T> next = new ArrayList<>(groups == null ? List.of() : groups);
        if (index < 0 || index >= next.size() || ops.isEmpty(next.get(index))) {
            return new Take<>(next, ops.empty());
        }
        return new Take<>(next, next.remove(index));
    }

    /**
     * Moves the groups at {@code indices} (duplicates and out-of-range ones ignored, handled in ascending order) into
     * the slots named by {@code targets}, first onto same-kind stacks and then into empty slots. A group that fits
     * entirely is removed; one that only partly fits stays where it is with what is left; one that does not fit at all
     * stays unchanged.
     */
    public static <T> List<T> restore(
            StackOps<T> ops,
            List<T> groups,
            int[] indices,
            int[] targets,
            IntFunction<T> getTarget,
            ObjIntConsumer<T> setTarget) {
        List<T> next = new ArrayList<>(groups == null ? List.of() : groups);
        TreeSet<Integer> order = new TreeSet<>();
        if (indices != null) {
            Arrays.stream(indices).filter(i -> i >= 0 && i < next.size()).forEach(order::add);
        }
        List<T> kept = new ArrayList<>(next.size());
        for (int i = 0; i < next.size(); i++) {
            T group = next.get(i);
            if (!order.contains(i) || ops.isEmpty(group)) {
                kept.add(group);
                continue;
            }
            T rest = QuickTransferLogic.moveInto(ops, group, targets, getTarget, setTarget);
            if (!ops.isEmpty(rest)) {
                kept.add(rest);
            }
        }
        return kept;
    }

    /** What is read back from disk or the wire: empty stacks dropped, at most {@link #MAX_GROUPS} kept (the first ones). */
    public static <T> List<T> sanitize(StackOps<T> ops, List<T> groups) {
        List<T> clean = new ArrayList<>();
        if (groups == null) {
            return clean;
        }
        for (T group : groups) {
            if (group != null && !ops.isEmpty(group) && clean.size() < MAX_GROUPS) {
                clean.add(group);
            }
        }
        return clean;
    }
}
