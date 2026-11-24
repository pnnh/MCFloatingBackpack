package pnnh.mcfloatingbackpack.inventory.nesting;

import java.util.List;

/**
 * 064: whether an item may go into a floating backpack. A floating backpack inside a floating backpack copies the whole inner
 * contents into the outer one, so the data grows by the fan-out of every level; the same holds for an item that carries one
 * (a shulker box holding a backpack, a bundle...). Pure logic over an abstract stack: the unit tests drive it with a fake
 * tree of stacks, {@link BackpackNesting} is the adapter for real item stacks.
 */
public final class NestingRules {
    /** How deep containers inside containers are looked into; deeper than this counts as "contains a backpack" (the safe answer). */
    public static final int MAX_DEPTH = 8;

    private NestingRules() {
    }

    public enum Verdict {
        OK,
        /** The item is a floating backpack. */
        IS_BACKPACK,
        /** The item carries a floating backpack somewhere inside it (or is nested deeper than can be checked). */
        CONTAINS_BACKPACK
    }

    /** What the rules need to know about a stack. */
    public interface Probe<T> {
        boolean isEmpty(T stack);

        boolean isBackpack(T stack);

        /** The stack carries items in a form that can be read piece by piece (a vanilla container or bundle component). */
        boolean hasContainerData(T stack);

        /** The stack carries data this mod cannot read piece by piece (another mod's component, free-form custom data). */
        boolean hasOpaqueData(T stack);

        /** The items inside a stack with {@link #hasContainerData}. */
        List<T> children(T stack);

        /** Whether the stack's data, written out, names a floating backpack (the fallback for opaque data). */
        boolean mentionsBackpack(T stack);
    }

    public static <T> Verdict check(Probe<T> probe, T stack) {
        if (probe.isEmpty(stack)) {
            return Verdict.OK;
        }
        if (probe.isBackpack(stack)) {
            return Verdict.IS_BACKPACK;
        }
        return carries(probe, stack, 0) ? Verdict.CONTAINS_BACKPACK : Verdict.OK;
    }

    private static <T> boolean carries(Probe<T> probe, T stack, int depth) {
        boolean container = probe.hasContainerData(stack);
        boolean opaque = probe.hasOpaqueData(stack);
        if (!container && !opaque) {
            return false; // nothing that can hold an item: the usual stack costs no more than this
        }
        if (depth >= MAX_DEPTH) {
            return true;
        }
        if (container) {
            for (T child : probe.children(stack)) {
                if (probe.isEmpty(child)) {
                    continue;
                }
                if (probe.isBackpack(child) || carries(probe, child, depth + 1)) {
                    return true;
                }
            }
        }
        return opaque && probe.mentionsBackpack(stack);
    }
}
