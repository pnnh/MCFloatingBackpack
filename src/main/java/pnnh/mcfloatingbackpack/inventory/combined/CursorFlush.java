package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

/**
 * 066: where the "all" tab's in-memory cursor goes when it has to be given up (the panel closed, the tab switched, the members changed, the
 * player left): into the members one after the other, then into the player's inventory, then onto the ground. Nothing is lost: what each step
 * takes is counted, and the three counts and what is left always add up to what was held. Pure: the stack type and the places are parameters.
 */
public final class CursorFlush {
    private CursorFlush() {
    }

    /**
     * @param placedInMembers how many each member took, in the order they were offered
     * @param placedInInventory how many the player's inventory took
     * @param dropped what is left to put on the ground (empty if nothing)
     */
    public record Result<T>(List<Integer> placedInMembers, int placedInInventory, T dropped) {
    }

    /**
     * @param held the cursor
     * @param members each takes a stack and returns what it could not take
     * @param inventory the player's inventory, the same
     * @param isEmpty whether a stack is empty
     * @param count how many items a stack holds
     */
    public static <T> Result<T> flush(
            T held,
            List<UnaryOperator<T>> members,
            UnaryOperator<T> inventory,
            Predicate<T> isEmpty,
            ToIntFunction<T> count) {
        List<Integer> placed = new ArrayList<>(members.size());
        T left = held;
        for (UnaryOperator<T> member : members) {
            if (isEmpty.test(left)) {
                placed.add(0);
                continue;
            }
            int before = count.applyAsInt(left);
            left = member.apply(left);
            placed.add(before - (isEmpty.test(left) ? 0 : count.applyAsInt(left)));
        }
        int inInventory = 0;
        if (!isEmpty.test(left)) {
            int before = count.applyAsInt(left);
            left = inventory.apply(left);
            inInventory = before - (isEmpty.test(left) ? 0 : count.applyAsInt(left));
        }
        return new Result<>(placed, inInventory, left);
    }
}
