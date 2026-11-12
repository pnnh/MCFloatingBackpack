package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.search.HiddenSummaryRows.Line;

/** 044: what {@code /hidden} lists, and how the words after it filter the categories. */
class HiddenSummaryRowsTest {
    private static final Line FOOD = new Line("food", "Food", 3);
    private static final Line FISHING = new Line("fishing", "Fishing", 2);
    private static final Line REDSTONE = new Line("redstone", "Redstone", 5);
    private static final Line CHINESE = new Line("tool", "工具", 4);
    private static final List<Line> ALL = List.of(FOOD, FISHING, REDSTONE, CHINESE);

    private static List<Line> visible(String rest, List<Line> lines) {
        // the words after the command are parsed as the rest of the query
        return HiddenSummaryRows.visible(lines, SearchQuery.parse("/hidden " + rest));
    }

    @Test
    void withoutWordsEveryLineIsShownInTheOrderGiven() {
        assertEquals(ALL, HiddenSummaryRows.visible(ALL, SearchQuery.parse("/hidden")));
        assertEquals(ALL, visible("", ALL));
    }

    @Test
    void aWordMustBeInTheNameOrTheId() {
        assertEquals(List.of(FISHING), visible("fish", ALL));
        assertEquals(List.of(FISHING), visible("FISH", ALL));
        assertEquals(List.of(FOOD), visible("foo", ALL));
        assertEquals(List.of(CHINESE), visible("工", ALL), "the name in the current language");
        assertEquals(List.of(CHINESE), visible("tool", ALL), "the id");
    }

    @Test
    void theGrammarOfTheWordsAppliesToTheCategories() {
        // an "o" is in food, redstone and (in the id) tool; fishing has none; -工 then takes tool out
        assertEquals(List.of(FOOD, REDSTONE), visible("o -fish -工", ALL));
        assertEquals(List.of(FOOD, FISHING), visible("food|fish", ALL));
        assertEquals(List.of(FISHING, REDSTONE, CHINESE), visible("-food", ALL));
        assertEquals(List.of(), visible("\"food fishing\"", ALL));
    }

    @Test
    void aModOrATagAlternativeNeverHoldsForACategoryRow() {
        assertEquals(List.of(), visible("@minecraft", ALL));
        assertEquals(List.of(), visible("#food", ALL));
        assertEquals(List.of(), visible("@food", ALL), "even where the id would match as a word");
        assertEquals(ALL, visible("-@minecraft", ALL), "their exclusions exclude nothing");
        assertEquals(ALL, visible("-#food", ALL));
        assertEquals(List.of(FOOD), visible("food @x|food", ALL), "an or with a word still lets the word through");
    }

    @Test
    void nothingHiddenIsNothingToShow() {
        assertEquals(List.of(), HiddenSummaryRows.visible(List.of(), SearchQuery.parse("/hidden")));
        assertEquals(0, HiddenSummaryRows.totalCells(List.of()));
    }

    @Test
    void theTotalIsTheCellsOfTheLinesShown() {
        assertEquals(14, HiddenSummaryRows.totalCells(ALL));
        assertEquals(2, HiddenSummaryRows.totalCells(visible("fish", ALL)));
        assertEquals(0, HiddenSummaryRows.totalCells(visible("@x", ALL)));
    }
}
