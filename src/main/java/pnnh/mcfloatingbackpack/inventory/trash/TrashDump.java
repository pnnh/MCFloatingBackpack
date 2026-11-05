package pnnh.mcfloatingbackpack.inventory.trash;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 022: which main-area slots a dump takes. A slot is taken when it holds an item whose category carries the trash tag,
 * is not on the protection list, and whose stack differs from the default stack by wear at most. The four questions
 * are asked through predicates, so this runs over real stacks in game and over strings in tests.
 */
public final class TrashDump {
    private TrashDump() {
    }

    /** @return the slots to dump, ascending */
    public static <T> int[] select(
            int size,
            IntFunction<T> stackAt,
            Predicate<T> isEmpty,
            Predicate<T> hasTrashTag,
            Predicate<T> isProtected,
            Predicate<T> onlyDefaultData) {
        List<Integer> picked = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            T stack = stackAt.apply(slot);
            if (stack != null
                    && !isEmpty.test(stack)
                    && hasTrashTag.test(stack)
                    && !isProtected.test(stack)
                    && onlyDefaultData.test(stack)) {
                picked.add(slot);
            }
        }
        return picked.stream().mapToInt(Integer::intValue).toArray();
    }
}
