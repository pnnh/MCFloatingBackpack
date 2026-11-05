package pnnh.mcfloatingbackpack.inventory.tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 018: fuzzy matching for the text box's drop-down. Case-insensitive; an entry matches when the query is a prefix of,
 * a substring of, or a subsequence of its id or its display name, in that order of preference; entries of the same
 * grade keep the order they were given in.
 */
public final class TagMatcher {
    /** A preset as the drop-down knows it: its stable id and the name shown in the current language. */
    public record Entry(String id, String displayName) {
    }

    private TagMatcher() {
    }

    public static List<String> match(String query, List<Entry> entries) {
        List<String> all = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            for (Entry entry : entries) {
                all.add(entry.id());
            }
            return all;
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<String> prefix = new ArrayList<>();
        List<String> substring = new ArrayList<>();
        List<String> subsequence = new ArrayList<>();
        for (Entry entry : entries) {
            int grade = Math.min(grade(q, entry.id()), grade(q, entry.displayName()));
            switch (grade) {
                case 0 -> prefix.add(entry.id());
                case 1 -> substring.add(entry.id());
                case 2 -> subsequence.add(entry.id());
                default -> {
                }
            }
        }
        List<String> out = new ArrayList<>(prefix);
        out.addAll(substring);
        out.addAll(subsequence);
        return out;
    }

    /** 0 prefix, 1 substring, 2 subsequence, 3 no match. */
    private static int grade(String lowerQuery, String text) {
        if (text == null) {
            return 3;
        }
        String t = text.toLowerCase(Locale.ROOT);
        if (t.startsWith(lowerQuery)) {
            return 0;
        }
        if (t.contains(lowerQuery)) {
            return 1;
        }
        int at = 0;
        for (int i = 0; i < t.length() && at < lowerQuery.length(); i++) {
            if (t.charAt(i) == lowerQuery.charAt(at)) {
                at++;
            }
        }
        return at == lowerQuery.length() ? 2 : 3;
    }
}
