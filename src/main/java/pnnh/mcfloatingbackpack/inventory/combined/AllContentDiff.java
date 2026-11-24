package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * 066: whether a member's contents are different from what the client was last sent. A save writes a new contents object every time (even for
 * no change) and the stacks inside have no equals, so: the very same object as last time means nothing was saved and nothing changed; a
 * different object is compared by what the client is shown (the main area, the rows, the tags), and only a difference there is sent. Pure.
 */
public final class AllContentDiff {
    private AllContentDiff() {
    }

    public static boolean changed(boolean sameObject, BooleanSupplier mainSame, BooleanSupplier rowsSame, BooleanSupplier tagsSame) {
        if (sameObject) {
            return false;
        }
        return !(mainSame.getAsBoolean() && rowsSame.getAsBoolean() && tagsSame.getAsBoolean());
    }

    /** Two lists hold the same things, compared one by one with {@code same}. */
    public static <T> boolean listsMatch(List<T> a, List<T> b, BiPredicate<T, T> same) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!same.test(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
