package pnnh.mcfloatingbackpack.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class TermsNormalizerTest {
    @Test
    void nullAndEmptyAreNothing() {
        assertEquals(List.of(), TermsNormalizer.normalize(null).terms());
        assertEquals(List.of(), TermsNormalizer.normalize(List.of()).terms());
        assertFalse(TermsNormalizer.normalize(null).tooMany());
        assertFalse(TermsNormalizer.normalize(null).tooLong());
    }

    @Test
    void blankAndNullTermsAreDropped() {
        TermsNormalizer.Result result = TermsNormalizer.normalize(Arrays.asList("zsg", "", "   ", null, "\t", "diamond"));
        assertEquals(List.of("zsg", "diamond"), result.terms());
        assertFalse(result.tooMany() || result.tooLong());
    }

    @Test
    void termsAreLowerCasedAndKeepTheirOrder() {
        assertEquals(List.of("zsg", "diamond pickaxe", "钻石镐"), TermsNormalizer.normalize(List.of("ZSG", "Diamond Pickaxe", "钻石镐")).terms());
    }

    @Test
    void lowerCasingIsIndependentOfTheLocale() {
        // a Turkish default locale would turn "I" into a dotless i; the normalizer must not
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals(List.of("iron"), TermsNormalizer.normalize(List.of("IRON")).terms());
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }

    @Test
    void duplicatesAreMergedAfterLowerCasing() {
        assertEquals(List.of("zsg"), TermsNormalizer.normalize(List.of("zsg", "ZSG", "zsg")).terms());
    }

    @Test
    void aTermOverTheLimitIsDroppedWholeNotCut() {
        String exactly = "a".repeat(SearchTermsLimits.MAX_TERM_LENGTH);
        String over = "b".repeat(SearchTermsLimits.MAX_TERM_LENGTH + 1);
        TermsNormalizer.Result result = TermsNormalizer.normalize(List.of(exactly, over, "ok"));
        assertEquals(List.of(exactly, "ok"), result.terms());
        assertTrue(result.tooLong());
        assertFalse(result.tooMany());
    }

    @Test
    void onlyTheFirstMaxTermsAreKept() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < SearchTermsLimits.MAX_TERMS + 10; i++) {
            many.add("t" + i);
        }
        TermsNormalizer.Result result = TermsNormalizer.normalize(many);
        assertEquals(SearchTermsLimits.MAX_TERMS, result.terms().size());
        assertEquals("t0", result.terms().get(0));
        assertEquals("t" + (SearchTermsLimits.MAX_TERMS - 1), result.terms().get(SearchTermsLimits.MAX_TERMS - 1));
        assertTrue(result.tooMany());
    }

    @Test
    void exactlyMaxTermsIsNotTooMany() {
        List<String> exact = new ArrayList<>();
        for (int i = 0; i < SearchTermsLimits.MAX_TERMS; i++) {
            exact.add("t" + i);
        }
        TermsNormalizer.Result result = TermsNormalizer.normalize(exact);
        assertEquals(SearchTermsLimits.MAX_TERMS, result.terms().size());
        assertFalse(result.tooMany());
    }

    @Test
    void duplicatesDoNotCountTowardsTheLimit() {
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < SearchTermsLimits.MAX_TERMS * 2; i++) {
            terms.add("same");
        }
        terms.add("other");
        TermsNormalizer.Result result = TermsNormalizer.normalize(terms);
        assertEquals(List.of("same", "other"), result.terms());
        assertFalse(result.tooMany());
    }

    @Test
    void aTermWithASpaceIsKept() {
        assertEquals(List.of("diamond pickaxe"), TermsNormalizer.normalize(List.of("Diamond Pickaxe")).terms());
    }
}
