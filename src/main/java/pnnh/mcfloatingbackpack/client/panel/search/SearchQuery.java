package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 042: what the player typed into the search box, parsed (grammar: specs/042-backpack-search-core/contracts/query-syntax.md).
 * The text is cut into <em>terms</em> at blanks, every term has to hold (and); a term is a list of <em>alternatives</em> cut at
 * {@code |}, any one of them is enough (or); an alternative that starts with {@code -} is an exclusion. A double quote switches
 * <em>quoted mode</em> on and off: inside it blanks, {@code |} and {@code -} are ordinary characters and the quote itself is not
 * part of the text; an unclosed quote runs to the end. Empty alternatives and empty terms are dropped. Texts are lower-cased
 * with {@link Locale#ROOT}. No Minecraft types.
 */
public final class SearchQuery {
    /** The longest query the box takes, in code points. */
    public static final int MAX_CODEPOINTS = 100;

    /** One way a term can hold: the text is contained (or, when {@code negated}, contained nowhere). */
    public record Alternative(boolean negated, String text) {
    }

    /** A list of alternatives, any one of which makes the term hold. */
    public record Term(List<Alternative> alternatives) {
    }

    private static final SearchQuery EMPTY = new SearchQuery(List.of());

    private final List<Term> terms;

    private SearchQuery(List<Term> terms) {
        this.terms = terms;
    }

    public static SearchQuery empty() {
        return EMPTY;
    }

    public List<Term> terms() {
        return terms;
    }

    /** No valid term: nothing is filtered or highlighted. */
    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public static SearchQuery parse(String input) {
        if (input == null || input.isEmpty()) {
            return EMPTY;
        }
        List<Term> terms = new ArrayList<>();
        List<Alternative> alternatives = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean quoted = false;
        // an alternative is an exclusion when its first character, outside quotes, is '-'
        boolean negated = false;
        boolean started = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                started = true;
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                finishAlternative(alternatives, text, negated);
                negated = false;
                started = false;
                finishTerm(terms, alternatives);
                continue;
            }
            if (!quoted && c == '|') {
                finishAlternative(alternatives, text, negated);
                negated = false;
                started = false;
                continue;
            }
            if (!quoted && c == '-' && !started) {
                negated = true;
                started = true;
                continue;
            }
            started = true;
            text.append(c);
        }
        finishAlternative(alternatives, text, negated);
        finishTerm(terms, alternatives);
        return terms.isEmpty() ? EMPTY : new SearchQuery(List.copyOf(terms));
    }

    private static void finishAlternative(List<Alternative> alternatives, StringBuilder text, boolean negated) {
        if (!text.isEmpty()) {
            alternatives.add(new Alternative(negated, text.toString().toLowerCase(Locale.ROOT)));
        }
        text.setLength(0);
    }

    private static void finishTerm(List<Term> terms, List<Alternative> alternatives) {
        if (!alternatives.isEmpty()) {
            terms.add(new Term(List.copyOf(alternatives)));
        }
        alternatives.clear();
    }
}
