package pnnh.mcfloatingbackpack.inventory.pickup;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

/**
 * 024: puts a picked-up stack into the backpack's main area (first onto matching stacks that have room, then into empty
 * slots, slot 0 to size-1) and reports what did not fit and which slots changed, so the caller can sync exactly those.
 * Built on {@link QuickTransferLogic#moveInto}, so the game and the tests run the same code. Pure logic over an abstract
 * stack type.
 */
public final class PickupInsert {
    /**
     * @param rest what did not fit (empty when everything did)
     * @param moved how many items went in
     * @param changedSlots the slots that were written, ascending, without duplicates
     */
    public record Result<T>(T rest, int moved, int[] changedSlots) {
    }

    private PickupInsert() {
    }

    public static <T> Result<T> insert(
            QuickTransferLogic.StackOps<T> ops, T stack, int size, IntFunction<T> get, ObjIntConsumer<T> set) {
        int[] order = new int[size];
        Arrays.setAll(order, i -> i);
        return insert(ops, stack, List.of(order), get, set);
    }

    /**
     * 035: the same, over segments of slots tried one after the other (the rows of the category extensions that take the stack
     * first, then the tier capacity's cells).
     */
    public static <T> Result<T> insert(
            QuickTransferLogic.StackOps<T> ops, T stack, List<int[]> segments, IntFunction<T> get, ObjIntConsumer<T> set) {
        if (ops.isEmpty(stack)) {
            return new Result<>(ops.empty(), 0, new int[0]);
        }
        TreeSet<Integer> changed = new TreeSet<>();
        T rest = QuickTransferLogic.moveIntoSegments(ops, stack, segments, get, (written, index) -> {
            set.accept(written, index);
            changed.add(index);
        });
        int moved = ops.count(stack) - (ops.isEmpty(rest) ? 0 : ops.count(rest));
        return new Result<>(rest, moved, changed.stream().mapToInt(Integer::intValue).toArray());
    }
}
