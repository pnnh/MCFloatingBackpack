package pnnh.mcfloatingbackpack.client.panel.search;

/**
 * 044: the summary of {@code /hidden} may need more text lines than the main area has (3 columns wide means words wrap a lot). This
 * decides what is shown. Priority: the category lines, then the title, then the hint. If the category lines alone do not fit, the last
 * line shown is an ellipsis (the whole text is in the tooltip). Pure arithmetic on line counts; no Minecraft types.
 */
public final class HiddenSummaryFit {
    /** How many category lines to draw, whether the title and the hint are drawn, and whether an ellipsis line ends the list. */
    public record Plan(int categoryLines, boolean showTitle, boolean showHint, boolean ellipsis) {
    }

    private HiddenSummaryFit() {
    }

    public static Plan fit(int categoryLinesNeeded, int titleLines, int hintLines, int availableLines) {
        if (availableLines <= 0) {
            return new Plan(0, false, false, false);
        }
        if (categoryLinesNeeded > availableLines) {
            // the last line is the ellipsis; with a single line available that is all there is
            return new Plan(availableLines - 1, false, false, true);
        }
        int remaining = availableLines - categoryLinesNeeded;
        boolean showTitle = titleLines <= remaining;
        if (showTitle) {
            remaining -= titleLines;
        }
        // a hint without its title would hang in the air
        boolean showHint = showTitle && hintLines <= remaining;
        return new Plan(categoryLinesNeeded, showTitle, showHint, false);
    }
}
