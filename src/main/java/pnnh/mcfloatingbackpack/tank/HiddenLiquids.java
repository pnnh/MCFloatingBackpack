package pnnh.mcfloatingbackpack.tank;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 048 (User Story 6): the liquids a backpack holds that cannot be reached because no tank counts for them: the amount is more than zero
 * and the bottom area has no tank of that liquid (a tank in a side area does not count). They are "hidden" like the items in a hidden
 * row (037): the title bar's yellow dot, its hint and {@code /hidden} (044) say so. Pure logic, no Minecraft types.
 */
public final class HiddenLiquids {
    /** One hidden liquid and how much of it there is. */
    public record Line(FluidKind kind, int amountMb) {
    }

    private HiddenLiquids() {
    }

    /** The hidden liquids, in the order of {@link FluidKind}; {@code hasTank} says whether a tank counts for a liquid. */
    public static List<Line> lines(FluidStore store, Predicate<FluidKind> hasTank) {
        List<Line> out = new ArrayList<>();
        for (FluidKind kind : FluidKind.values()) {
            int amount = store.amount(kind);
            if (amount > 0 && !hasTank.test(kind)) {
                out.add(new Line(kind, amount));
            }
        }
        return out;
    }
}
