package pnnh.mcfloatingbackpack.client.panel.search;

import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery.Alternative;
import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery.Term;

/**
 * 042: whether a cell matches a query. Every term has to hold; a term holds when any of its alternatives does; an alternative
 * holds when the text is contained (or, for an exclusion, contained nowhere). An empty query matches everything. No Minecraft
 * types.
 */
public final class SearchMatcher {
    private SearchMatcher() {
    }

    public static boolean matches(SearchQuery query, TextMatcher haystack) {
        for (Term term : query.terms()) {
            if (!holds(term, haystack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(Term term, TextMatcher haystack) {
        for (Alternative alternative : term.alternatives()) {
            boolean contained = haystack.contains(alternative.text());
            if (alternative.negated() ? !contained : contained) {
                return true;
            }
        }
        return false;
    }
}
