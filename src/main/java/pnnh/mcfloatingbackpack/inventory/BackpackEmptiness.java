package pnnh.mcfloatingbackpack.inventory;

import java.util.List;
import java.util.function.Predicate;

/**
 * 065: whether a backpack holds nothing at all, which is what the combined backpack's crafting recipe asks of the backpack it takes: a
 * backpack with anything in it would vanish together with it. Pure and generic in the stack type, so it is tested without the game.
 *
 * <p>What counts as holding something: any item in the main area, the cursor, any of the three tool areas, the trash can, any cell of a
 * category row (visible or hidden), or any liquid. The tag settings and the pickup blacklist are settings, not contents, and do not count.
 */
public final class BackpackEmptiness {
    private BackpackEmptiness() {
    }

    /** The parts of a backpack the rule looks at. */
    public record Parts<T>(
            List<T> main, T cursor, List<T> left, List<T> right, List<T> bottom, List<T> trash, List<List<T>> rowCells, boolean fluidsEmpty) {
    }

    public static <T> boolean isEmpty(Parts<T> parts, Predicate<T> emptyStack) {
        if (!parts.fluidsEmpty() || !emptyStack.test(parts.cursor())) {
            return false;
        }
        for (List<T> list : List.of(parts.main(), parts.left(), parts.right(), parts.bottom(), parts.trash())) {
            for (T stack : list) {
                if (!emptyStack.test(stack)) {
                    return false;
                }
            }
        }
        for (List<T> row : parts.rowCells()) {
            for (T stack : row) {
                if (!emptyStack.test(stack)) {
                    return false;
                }
            }
        }
        return true;
    }
}
