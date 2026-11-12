package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

/** 042: the list of slots a query shows. */
class SearchResultsTest {
    private static final String[] NAMES = {"oak log", null, "stripped oak log", "oak planks", null, "iron axe", "oak log"};

    private static IntFunction<TextMatcher> matchers() {
        return slot -> NAMES[slot] == null ? null : TextMatcher.ofTexts(List.of(NAMES[slot]));
    }

    @Test
    void anEmptyQueryIsNotFilteringAtAll() {
        assertNull(SearchResults.compute(SearchQuery.parse(""), NAMES.length, matchers()));
        assertNull(SearchResults.compute(SearchQuery.parse(" - | "), NAMES.length, matchers()));
    }

    @Test
    void theMatchingSlotsComeBackAscending() {
        assertArrayEquals(new int[] {0, 2, 6}, SearchResults.compute(SearchQuery.parse("oak log"), NAMES.length, matchers()));
    }

    @Test
    void emptyCellsNeverMatchEvenForExclusions() {
        // slots 1 and 4 are empty: "-planks" is "everything with an item that is not planks"
        assertArrayEquals(new int[] {0, 2, 5, 6}, SearchResults.compute(SearchQuery.parse("-planks"), NAMES.length, matchers()));
    }

    @Test
    void nothingMatchingGivesAnEmptyListNotNull() {
        assertArrayEquals(new int[0], SearchResults.compute(SearchQuery.parse("zzz"), NAMES.length, matchers()));
    }

    @Test
    void equalNamesEachTakeTheirOwnPlace() {
        assertArrayEquals(new int[] {0, 6}, SearchResults.compute(SearchQuery.parse("\"oak log\" -stripped"), NAMES.length, matchers()));
    }

    @Test
    void theCountOnlyCountsSlotsThatHoldSomethingNow() {
        int[] slots = SearchResults.compute(SearchQuery.parse("oak log"), NAMES.length, matchers());
        Set<Integer> items = new HashSet<>(Set.of(0, 2, 6));
        assertEquals(3, SearchResults.liveCount(slots, items::contains));
        items.remove(2);
        // slot 2 stays in the list as an empty place, but is no longer counted
        assertArrayEquals(new int[] {0, 2, 6}, slots);
        assertEquals(2, SearchResults.liveCount(slots, items::contains));
        assertEquals(0, SearchResults.liveCount(null, items::contains));
    }

    @Test
    void aCommandShowsNoCellsWhateverTheTextsSay() {
        // 044: /hidden draws its own summary and an unknown command finds nothing: both are "filtering" with an empty list
        for (String input : new String[] {"/hidden", "/hidden oak", "/foo", "/", "/hidden @x"}) {
            int[] slots = SearchResults.compute(SearchQuery.parse(input), NAMES.length, matchers());
            assertArrayEquals(new int[0], slots, input);
        }
        assertEquals(0, SearchResults.liveCount(new int[0], slot -> true));
    }

    @Test
    void aQueryWithoutACommandStillFiltersAndAnEmptyOneStillDoesNot() {
        assertArrayEquals(new int[] {0, 2, 6}, SearchResults.compute(SearchQuery.parse("oak log"), NAMES.length, matchers()));
        assertEquals(null, SearchResults.compute(SearchQuery.parse("  "), NAMES.length, matchers()));
        assertEquals(null, SearchResults.compute(SearchQuery.parse("@"), NAMES.length, matchers()));
    }
}
