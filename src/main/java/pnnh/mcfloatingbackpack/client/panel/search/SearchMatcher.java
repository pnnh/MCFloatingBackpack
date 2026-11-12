package pnnh.mcfloatingbackpack.client.panel.search;

import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery.Alternative;
import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery.Term;

/**
 * 042: whether a cell matches a query. Every term has to hold; a term holds when any of its alternatives does; an alternative
 * holds when the text is found (or, for an exclusion, found nowhere): a word among the item's texts, a {@code @} text among its mods (044), a {@code #} text among its tags (044). An empty query matches everything. No Minecraft
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
            boolean contained = switch (alternative.kind()) {
                case WORD -> haystack.contains(alternative.text());
                case MOD -> haystack.inMod(alternative.text());
                case TAG -> haystack.hasTag(alternative.text());
            };
            if (alternative.negated() ? !contained : contained) {
                return true;
            }
        }
        return false;
    }
}
