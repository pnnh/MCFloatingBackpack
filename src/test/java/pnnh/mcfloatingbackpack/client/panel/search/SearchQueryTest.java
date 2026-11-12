package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery.Alternative;

/** 042: the grammar table in contracts/query-syntax.md, line by line. */
class SearchQueryTest {
    /** Compact form: terms joined by ',', alternatives by '/', exclusions marked with a leading '!'. */
    private static String show(String input) {
        List<String> terms = new ArrayList<>();
        for (SearchQuery.Term term : SearchQuery.parse(input).terms()) {
            List<String> alternatives = new ArrayList<>();
            for (Alternative alternative : term.alternatives()) {
                alternatives.add((alternative.negated() ? "!" : "") + alternative.text());
            }
            terms.add(String.join("/", alternatives));
        }
        return String.join(",", terms);
    }

    @Test
    void emptyInputsAreEmptyQueries() {
        for (String input : new String[] {"", "   ", "-", "|", "- |", "\"\"", null}) {
            assertTrue(SearchQuery.parse(input).isEmpty(), String.valueOf(input));
        }
    }

    @Test
    void aWordIsOneTerm() {
        assertEquals("pick", show("pick"));
    }

    @Test
    void blanksSeparateTermsAndAreLoweredAndCollapsed() {
        assertEquals("iron,pick", show("Iron  PICK"));
        assertEquals("iron,pick", show("  iron\tpick  "));
    }

    @Test
    void aBarMakesAlternativesAndEmptyOnesAreDropped() {
        assertEquals("iron/gold", show("iron|gold"));
        assertEquals("iron", show("iron|"));
        assertEquals("iron", show("|iron"));
        assertEquals("iron/gold", show("iron||gold"));
    }

    @Test
    void aLeadingMinusExcludes() {
        assertEquals("!planks", show("-planks"));
        assertEquals("log,!stripped", show("log -stripped"));
    }

    @Test
    void aMinusInsideAWordIsPlain() {
        assertEquals("a-b", show("a-b"));
        assertEquals("iron/!gold", show("iron|-gold"));
    }

    @Test
    void quotesMakeOneUnitAndTheQuoteIsNotPartOfTheText() {
        assertEquals("oak log", show("\"oak log\""));
        assertEquals("!oak log", show("-\"oak log\""));
    }

    @Test
    void aQuoteNeverClosedRunsToTheEnd() {
        assertEquals("oak lo", show("\"oak lo"));
    }

    @Test
    void insideQuotesBarsBlanksAndMinusArePlain() {
        assertEquals("a | b,c", show("\"a | b\" c"));
        assertEquals("-x", show("\"-x\""));
    }

    @Test
    void theFutureSymbolsAreOrdinaryCharactersForNow() {
        assertEquals("@iron,#x,/hidden", show("@iron #x /hidden"));
    }
}
