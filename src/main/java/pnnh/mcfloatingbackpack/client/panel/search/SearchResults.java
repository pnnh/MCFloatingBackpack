package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * 042: which backpack slots a query shows. The result is a list of slot numbers in ascending order; the main area shows exactly
 * those, packed from the top left. An empty cell never matches (the caller gives no matcher for it). The list is worked out when
 * the query changes and then kept: a slot whose item is taken away stays in it (an empty place in the same spot) until the next
 * query. No Minecraft types.
 */
public final class SearchResults {
    private SearchResults() {
    }

    /**
     * @param matcherOf the searchable text of slot {@code i}, or null when the slot is empty
     * @return the matching slot numbers, ascending; null for an empty query (nothing is filtered)
     */
    public static int[] compute(SearchQuery query, int slotCount, IntFunction<TextMatcher> matcherOf) {
        if (query.isEmpty()) {
            return null;
        }
        if (query.command().type() != SearchQuery.Command.Type.NONE) {
            // 044: a command shows no cells (/hidden draws its own summary, an unknown command finds nothing)
            return new int[0];
        }
        int[] found = new int[slotCount];
        int count = 0;
        for (int slot = 0; slot < slotCount; slot++) {
            TextMatcher matcher = matcherOf.apply(slot);
            if (matcher != null && SearchMatcher.matches(query, matcher)) {
                found[count++] = slot;
            }
        }
        return java.util.Arrays.copyOf(found, count);
    }

    /** How many of the listed slots hold an item right now (the number shown in the box). */
    public static int liveCount(int[] slots, IntPredicate hasItem) {
        if (slots == null) {
            return 0;
        }
        int live = 0;
        for (int slot : slots) {
            if (hasItem.test(slot)) {
                live++;
            }
        }
        return live;
    }
}
