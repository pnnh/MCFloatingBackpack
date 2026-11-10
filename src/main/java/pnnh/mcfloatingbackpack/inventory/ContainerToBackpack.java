package pnnh.mcfloatingbackpack.inventory;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

/**
 * Takes the stack out of one container slot and puts as much as fits into the floating backpack;
 * whatever does not fit goes back where it came from (012, reverse direction). Over abstract stacks
 * and slots so it has no Minecraft dependency and can be unit-tested with fake slots;
 * {@link ContainerMenuAccess} supplies the adapter for real {@code Slot}s.
 *
 * <p>Unlike delivering <em>into</em> a container, this cannot reuse the menu's own shift-click rule:
 * for a container slot vanilla shift-click moves the stack into the player inventory, never anywhere
 * else.
 */
public final class ContainerToBackpack {
    private ContainerToBackpack() {
    }

    public interface SlotAccess<T> {
        T get();

        /**
         * {@code slot.allowModification(player)}: the stack can be picked up <em>and</em> put back.
         * Output-style slots (crafting result, furnace output, anvil, trades) refuse every stack, so
         * they are not a source: taking from them triggers crafting, trades or experience.
         */
        boolean canTakeAndPlaceBack();

        /** {@code slot.safeTake(count, count, player)}: removes up to {@code count}, empty if refused. */
        T take(int count);

        /** {@code slot.safeInsert}: returns what did not fit (the argument itself if nothing fit). */
        T insert(T stack);

        /** Puts a stack straight into the slot. */
        void set(T stack);
    }

    /** Last places for items the origin slot refuses to take back. */
    public interface Fallback<T> {
        /** @return what did not fit in the player inventory */
        T toInventory(T stack);

        void drop(T stack);
    }

    /**
     * @param moved how many items went into the backpack
     * @param skipped the slot was empty, not a valid source, or refused to give anything up
     * @param usedFallback the remainder could not be handed back through the slot's normal insert
     * @param dropped even the player inventory could not hold the remainder
     */
    public record Result(int moved, boolean skipped, boolean usedFallback, boolean dropped) {
    }

    public static <T> Result take(
            QuickTransferLogic.StackOps<T> ops,
            SlotAccess<T> slot,
            int[] backpackTargets,
            IntFunction<T> getBackpack,
            ObjIntConsumer<T> setBackpack,
            Fallback<T> fallback) {
        return take(ops, slot, stack -> List.<int[]>of(backpackTargets), getBackpack, setBackpack, fallback);
    }

    /** 035: the target slots come in segments, chosen per stack (the rows of the category extensions that take it first). */
    public static <T> Result take(
            QuickTransferLogic.StackOps<T> ops,
            SlotAccess<T> slot,
            Function<T, List<int[]>> backpackTargets,
            IntFunction<T> getBackpack,
            ObjIntConsumer<T> setBackpack,
            Fallback<T> fallback) {
        T current = slot.get();
        if (ops.isEmpty(current) || !slot.canTakeAndPlaceBack()) {
            return new Result(0, true, false, false);
        }
        T removed = slot.take(ops.count(current));
        if (ops.isEmpty(removed)) {
            return new Result(0, true, false, false);
        }
        int removedCount = ops.count(removed);
        T rest = QuickTransferLogic.moveIntoSegments(ops, removed, backpackTargets.apply(removed), getBackpack, setBackpack);
        int moved = removedCount - ops.count(rest);

        boolean usedFallback = false;
        boolean dropped = false;
        if (!ops.isEmpty(rest)) {
            // safeInsert mutates its argument, so hand it a copy.
            T remaining = slot.insert(ops.withCount(rest, ops.count(rest)));
            if (!ops.isEmpty(remaining)) {
                usedFallback = true;
                if (ops.isEmpty(slot.get())) {
                    slot.set(remaining);
                    remaining = ops.empty();
                }
            }
            if (!ops.isEmpty(remaining)) {
                remaining = fallback.toInventory(remaining);
            }
            if (!ops.isEmpty(remaining)) {
                fallback.drop(remaining);
                dropped = true;
            }
        }
        return new Result(moved, false, usedFallback, dropped);
    }
}
