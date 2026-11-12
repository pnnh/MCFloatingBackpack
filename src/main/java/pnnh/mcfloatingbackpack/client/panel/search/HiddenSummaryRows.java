package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 044: what {@code /hidden} lists: the categories that have items hidden (037) and in how many cells. The hidden items themselves are
 * not on the client, only this summary, so the words after {@code /hidden} can only filter the <em>categories</em>: a word has to be in
 * the category's name in the current language or in its id. They are asked with the same grammar as everything else (and, {@code |},
 * {@code -}, quotes); a {@code @mod} or {@code #tag} alternative is about items and a category row has none, so it never holds
 * ({@link TextMatcher}'s defaults). No Minecraft types.
 */
public final class HiddenSummaryRows {
    /** One category with something hidden: its id, its name in the current language, and how many cells hold something. */
    public record Line(String categoryId, String displayName, int cells) {
    }

    private HiddenSummaryRows() {
    }

    /** The lines the query lets through, in the order given. */
    public static List<Line> visible(List<Line> lines, SearchQuery rest) {
        List<Line> out = new ArrayList<>();
        for (Line line : lines) {
            TextMatcher row = TextMatcher.ofTexts(
                    List.of(line.displayName().toLowerCase(Locale.ROOT), line.categoryId().toLowerCase(Locale.ROOT)));
            if (SearchMatcher.matches(rest, row)) {
                out.add(line);
            }
        }
        return out;
    }

    /** The number shown in the box: all the cells the lines stand for. */
    public static int totalCells(List<Line> lines) {
        int total = 0;
        for (Line line : lines) {
            total += line.cells();
        }
        return total;
    }
}
