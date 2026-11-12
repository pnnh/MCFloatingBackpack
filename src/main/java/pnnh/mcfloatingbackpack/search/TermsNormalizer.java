package pnnh.mcfloatingbackpack.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 043: what a provider returned, made safe to match against: {@code null} is nothing, blank terms are dropped, terms are lower-cased,
 * a term longer than {@link SearchTermsLimits#MAX_TERM_LENGTH} is dropped as a whole, duplicates are merged and only the first
 * {@link SearchTermsLimits#MAX_TERMS} terms are kept. Pure logic with no Minecraft types.
 */
public final class TermsNormalizer {
    /** The safe terms (in the order given) and what had to be thrown away. */
    public record Result(List<String> terms, boolean tooMany, boolean tooLong) {
        static final Result EMPTY = new Result(List.of(), false, false);
    }

    private TermsNormalizer() {
    }

    public static Result normalize(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Result.EMPTY;
        }
        Set<String> kept = new LinkedHashSet<>();
        boolean tooMany = false;
        boolean tooLong = false;
        for (String term : raw) {
            if (term == null || term.isBlank()) {
                continue;
            }
            if (term.length() > SearchTermsLimits.MAX_TERM_LENGTH) {
                tooLong = true;
                continue;
            }
            String lower = term.toLowerCase(Locale.ROOT);
            if (kept.contains(lower)) {
                continue;
            }
            if (kept.size() >= SearchTermsLimits.MAX_TERMS) {
                tooMany = true;
                break;
            }
            kept.add(lower);
        }
        return new Result(List.copyOf(new ArrayList<>(kept)), tooMany, tooLong);
    }
}
