package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 042: what the player typed into the search box, parsed (grammar: specs/044-search-prefix-syntax/contracts/query-syntax.md, which
 * holds 042's too). The text is cut into <em>terms</em> at blanks, every term has to hold (and); a term is a list of
 * <em>alternatives</em> cut at {@code |}, any one of them is enough (or); an alternative that starts with {@code -} is an exclusion.
 * A double quote switches <em>quoted mode</em> on and off: inside it blanks, {@code |}, {@code -} and the prefixes are ordinary
 * characters and the quote itself is not part of the text; an unclosed quote runs to the end. Empty alternatives and empty terms are
 * dropped. Texts are lower-cased with {@link Locale#ROOT}.
 *
 * <p>044: an alternative that starts (after an optional {@code -}) with an unquoted {@code @} is about the item's <em>mod</em>, one
 * that starts with {@code #} is about its <em>tags</em>; anywhere else those characters are ordinary. A query that starts (after
 * blanks) with {@code /} begins with a <em>command</em>: its first word is the command ({@code /hidden}, or an unknown one) and the
 * rest is parsed as above. No Minecraft types.
 */
public final class SearchQuery {
    /** The longest query the box takes, in code points. */
    public static final int MAX_CODEPOINTS = 100;

    /** What an alternative is about: the item's texts, its mod ({@code @}) or its tags ({@code #}). */
    public enum Kind {
        WORD,
        MOD,
        TAG
    }

    /** One way a term can hold: the text is found (or, when {@code negated}, found nowhere). */
    public record Alternative(Kind kind, boolean negated, String text) {
        /** An ordinary word (042's form). */
        public Alternative(boolean negated, String text) {
            this(Kind.WORD, negated, text);
        }
    }

    /** A list of alternatives, any one of which makes the term hold. */
    public record Term(List<Alternative> alternatives) {
    }

    /** The command a query begins with: {@code /hidden}, an unknown one ({@link #raw} is what was typed), or none. */
    public record Command(Type type, String raw) {
        public enum Type {
            NONE,
            HIDDEN,
            UNKNOWN
        }

        public static final Command NONE = new Command(Type.NONE, "");
    }

    private static final SearchQuery EMPTY = new SearchQuery(List.of(), Command.NONE);
    private static final String HIDDEN = "/hidden";

    private final List<Term> terms;
    private final Command command;

    private SearchQuery(List<Term> terms, Command command) {
        this.terms = terms;
        this.command = command;
    }

    public static SearchQuery empty() {
        return EMPTY;
    }

    /** The terms; for a query with a command, the part after it. */
    public List<Term> terms() {
        return terms;
    }

    public Command command() {
        return command;
    }

    /** No valid term and no command: nothing is filtered or highlighted. */
    public boolean isEmpty() {
        return terms.isEmpty() && command.type() == Command.Type.NONE;
    }

    public static SearchQuery parse(String input) {
        if (input == null || input.isEmpty()) {
            return EMPTY;
        }
        Command command = Command.NONE;
        String body = input;
        String stripped = input.stripLeading();
        if (stripped.startsWith("/")) {
            int end = 0;
            while (end < stripped.length() && !Character.isWhitespace(stripped.charAt(end))) {
                end++;
            }
            String word = stripped.substring(0, end);
            command = new Command(word.toLowerCase(Locale.ROOT).equals(HIDDEN) ? Command.Type.HIDDEN : Command.Type.UNKNOWN, word);
            body = stripped.substring(end);
        }
        List<Term> terms = parseTerms(body);
        if (terms.isEmpty() && command.type() == Command.Type.NONE) {
            return EMPTY;
        }
        return new SearchQuery(List.copyOf(terms), command);
    }

    private static List<Term> parseTerms(String input) {
        List<Term> terms = new ArrayList<>();
        List<Alternative> alternatives = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean quoted = false;
        // an alternative is an exclusion when its first character, outside quotes, is '-'
        boolean negated = false;
        boolean started = false;
        // a prefix may only be the first thing of an alternative (after the '-'): no text, no quote before it
        boolean prefixAllowed = true;
        Kind kind = Kind.WORD;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                started = true;
                prefixAllowed = false;
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                finishAlternative(alternatives, text, negated, kind);
                negated = false;
                started = false;
                prefixAllowed = true;
                kind = Kind.WORD;
                finishTerm(terms, alternatives);
                continue;
            }
            if (!quoted && c == '|') {
                finishAlternative(alternatives, text, negated, kind);
                negated = false;
                started = false;
                prefixAllowed = true;
                kind = Kind.WORD;
                continue;
            }
            if (!quoted && c == '-' && !started) {
                negated = true;
                started = true;
                continue;
            }
            if (!quoted && prefixAllowed && (c == '@' || c == '#')) {
                kind = c == '@' ? Kind.MOD : Kind.TAG;
                prefixAllowed = false;
                started = true;
                continue;
            }
            started = true;
            prefixAllowed = false;
            text.append(c);
        }
        finishAlternative(alternatives, text, negated, kind);
        finishTerm(terms, alternatives);
        return terms;
    }

    private static void finishAlternative(List<Alternative> alternatives, StringBuilder text, boolean negated, Kind kind) {
        if (!text.isEmpty()) {
            alternatives.add(new Alternative(kind, negated, text.toString().toLowerCase(Locale.ROOT)));
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
