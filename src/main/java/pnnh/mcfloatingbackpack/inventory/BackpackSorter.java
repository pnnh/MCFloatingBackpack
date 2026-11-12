package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic.StackOps;
import pnnh.mcfloatingbackpack.inventory.extension.CategoryMatcher;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionRules;

/**
 * 047: tidying the main area of a backpack: stacks of the same kind are merged (as few stacks as possible: full ones first, the
 * rest in one last stack), the kinds are put in the order the caller gives, and every stack goes to the first free cell of the first
 * segment that has one, the segments being those of {@link ExtensionRules#targetSegments}: the cells of the visible category rows
 * the stack fits (row by row), then the tier capacity's cells, and last the universal rows' cells. So a row that takes food is
 * filled with food before any food goes to the main cells, and a universal row is only used when the main cells are full. What
 * is not a whole stack of a kind stays one stack: nothing is split further.
 *
 * <p>Safety first: the new layout is only returned when every kind has exactly the total it had, no cell is over its stack limit,
 * every cell of a row holds something that row takes, and every stack found a place. Otherwise the answer is {@code null} and the
 * caller changes nothing. Written over an abstract stack type so it has no Minecraft dependency and is unit-tested with the very
 * code that runs in the game ({@link ItemStackTransferOps} and {@code ItemSortOrder} are the production adapters).
 */
public final class BackpackSorter {
    private BackpackSorter() {
    }

    /**
     * @param slots             the main area's cells, in order (the tier capacity's cells, then the cells of the visible rows)
     * @param baseCapacity      how many of them are the tier capacity's
     * @param visibleCategories the categories of the visible rows, in order (nine cells each)
     * @param kindOrder         how two kinds are ordered (the stacks of one kind are never apart)
     * @return the new cells, same size; or {@code null} when it cannot be done safely (nothing may be written then)
     */
    public static <T> List<T> sort(
            List<T> slots,
            int baseCapacity,
            List<String> visibleCategories,
            CategoryMatcher<T> matcher,
            StackOps<T> ops,
            Comparator<T> kindOrder) {
        int size = slots.size();
        // 1. the kinds present and how many of each
        List<T> kinds = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        for (T stack : slots) {
            if (ops.isEmpty(stack)) {
                continue;
            }
            int kind = indexOfKind(kinds, stack, ops);
            if (kind < 0) {
                kinds.add(stack);
                totals.add((long) ops.count(stack));
            } else {
                totals.set(kind, totals.get(kind) + ops.count(stack));
            }
        }
        // 2. the kinds in order (a stable sort: kinds that compare equal stay in the order they were found)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> kindOrder.compare(kinds.get(a), kinds.get(b)));
        // 3. the stacks: full ones first, then what is left of the kind
        List<T> stacks = new ArrayList<>();
        for (int kind : order) {
            T representative = kinds.get(kind);
            long total = totals.get(kind);
            int max = Math.max(1, ops.maxStack(representative));
            while (total > 0) {
                int count = (int) Math.min(max, total);
                stacks.add(ops.withCount(representative, count));
                total -= count;
            }
        }
        // 4. each stack into the first free cell of the first segment that has one
        List<T> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(ops.empty());
        }
        for (T stack : stacks) {
            int cell = firstFreeCell(
                    ExtensionRules.targetSegments(visibleCategories, baseCapacity, stack, matcher), result, size, ops);
            if (cell < 0) {
                return null;
            }
            result.set(cell, stack);
        }
        // 5. nothing lost, nothing made up, nothing over a limit, nothing in a row that does not take it
        if (!safe(slots, result, baseCapacity, visibleCategories, matcher, ops)) {
            return null;
        }
        return result;
    }

    private static <T> int indexOfKind(List<T> kinds, T stack, StackOps<T> ops) {
        for (int i = 0; i < kinds.size(); i++) {
            if (ops.sameKind(kinds.get(i), stack)) {
                return i;
            }
        }
        return -1;
    }

    private static <T> int firstFreeCell(List<int[]> segments, List<T> cells, int size, StackOps<T> ops) {
        for (int[] segment : segments) {
            for (int cell : segment) {
                if (cell >= 0 && cell < size && ops.isEmpty(cells.get(cell))) {
                    return cell;
                }
            }
        }
        return -1;
    }

    private static <T> boolean safe(
            List<T> before,
            List<T> after,
            int baseCapacity,
            List<String> visibleCategories,
            CategoryMatcher<T> matcher,
            StackOps<T> ops) {
        List<T> kinds = new ArrayList<>();
        List<Long> was = new ArrayList<>();
        for (T stack : before) {
            if (!ops.isEmpty(stack)) {
                int kind = indexOfKind(kinds, stack, ops);
                if (kind < 0) {
                    kinds.add(stack);
                    was.add((long) ops.count(stack));
                } else {
                    was.set(kind, was.get(kind) + ops.count(stack));
                }
            }
        }
        long[] now = new long[kinds.size()];
        for (int cell = 0; cell < after.size(); cell++) {
            T stack = after.get(cell);
            if (ops.isEmpty(stack)) {
                continue;
            }
            int kind = indexOfKind(kinds, stack, ops);
            if (kind < 0 || ops.count(stack) > Math.max(1, ops.maxStack(stack))) {
                return false;
            }
            now[kind] += ops.count(stack);
            if (cell >= baseCapacity) {
                int row = (cell - baseCapacity) / ExtensionCategories.ROW_CELLS;
                if (row >= visibleCategories.size() || !matcher.matches(visibleCategories.get(row), stack)) {
                    return false;
                }
            }
        }
        for (int kind = 0; kind < kinds.size(); kind++) {
            if (now[kind] != was.get(kind)) {
                return false;
            }
        }
        return true;
    }
}
