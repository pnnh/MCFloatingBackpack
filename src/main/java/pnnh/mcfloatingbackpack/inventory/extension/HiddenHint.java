package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.ArrayList;
import java.util.List;

/**
 * 037: what the panel says about the items it cannot show (the rows hidden when their extension was taken away): which categories
 * have something hidden and in how many cells. The panel is told the number of cells that hold something, not the number of items
 * (035), so that is what is said. Pure logic with no Minecraft types.
 */
public final class HiddenHint {
    /** One line of the hint. */
    public record Line(String category, int cells) {
    }

    private HiddenHint() {
    }

    /** The categories with at least one cell holding something, in the fixed category order (unknown ids last, as they came). */
    public static List<Line> lines(List<RowSet.HiddenSummary> summaries) {
        List<Line> out = new ArrayList<>();
        for (RowSet.HiddenSummary summary : summaries) {
            if (summary.nonEmptyCells() > 0) {
                out.add(new Line(summary.category(), summary.nonEmptyCells()));
            }
        }
        // A stable sort: ids this version does not know share the last place and keep the order they came in.
        out.sort((a, b) -> Integer.compare(ExtensionCategories.orderOf(a.category()), ExtensionCategories.orderOf(b.category())));
        return out;
    }

    /** Whether there is anything to say (the yellow dot is drawn exactly then). */
    public static boolean any(List<RowSet.HiddenSummary> summaries) {
        return !lines(summaries).isEmpty();
    }
}
