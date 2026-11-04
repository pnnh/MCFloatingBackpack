package pnnh.mcfloatingbackpack.inventory;

import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

/**
 * Moves one stack into a list of target slots: first top up same-kind stacks, then fill empty
 * slots; whatever does not fit is returned. Written over an abstract stack type so it has no
 * Minecraft dependency and can be unit-tested with the very same code that runs in game
 * ({@link ItemStackTransferOps} is the production adapter).
 */
public final class QuickTransferLogic {
    private QuickTransferLogic() {
    }

    public interface StackOps<T> {
        T empty();

        boolean isEmpty(T stack);

        int count(T stack);

        /** Stack limit of this kind of item. */
        int maxStack(T stack);

        boolean sameKind(T a, T b);

        /** Copy of {@code stack} with the given count; an empty stack when count is not positive. */
        T withCount(T stack, int count);
    }

    /**
     * @param targetOrder slot indices in the order they are tried; indices left out are never written
     * @return what could not be moved (empty when everything fit)
     */
    public static <T> T moveInto(
            StackOps<T> ops, T source, int[] targetOrder, IntFunction<T> getTarget, ObjIntConsumer<T> setTarget) {
        if (ops.isEmpty(source)) {
            return ops.empty();
        }
        int remaining = ops.count(source);

        for (int index : targetOrder) {
            if (remaining <= 0) {
                break;
            }
            T target = getTarget.apply(index);
            if (ops.isEmpty(target) || !ops.sameKind(target, source)) {
                continue;
            }
            int room = ops.maxStack(target) - ops.count(target);
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining);
            setTarget.accept(ops.withCount(target, ops.count(target) + moved), index);
            remaining -= moved;
        }

        for (int index : targetOrder) {
            if (remaining <= 0) {
                break;
            }
            if (!ops.isEmpty(getTarget.apply(index))) {
                continue;
            }
            int moved = Math.min(ops.maxStack(source), remaining);
            setTarget.accept(ops.withCount(source, moved), index);
            remaining -= moved;
        }

        return remaining <= 0 ? ops.empty() : ops.withCount(source, remaining);
    }
}
