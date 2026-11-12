package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 042: and, or, exclusion and whole phrases against a cell's texts. */
class SearchMatcherTest {
    private static boolean hit(String query, String... texts) {
        return SearchMatcher.matches(SearchQuery.parse(query), TextMatcher.ofTexts(List.of(texts)));
    }

    @Test
    void anEmptyQueryMatchesEverything() {
        assertTrue(hit("", "oak log"));
        assertTrue(hit("   ", "anything"));
    }

    @Test
    void aWordIsASubstringAnywhereInAnyText() {
        assertTrue(hit("pick", "diamond pickaxe", "diamond_pickaxe"));
        assertTrue(hit("pickaxe", "iron pickaxe"));
        assertFalse(hit("axe2", "iron axe"));
        assertTrue(hit("diamond_p", "diamond pickaxe", "diamond_pickaxe"));
    }

    @Test
    void termsAreAndedInAnyOrder() {
        assertTrue(hit("oak log", "oak log"));
        assertTrue(hit("oak log", "stripped oak log"));
        assertTrue(hit("log oak", "oak log"));
        assertFalse(hit("oak log", "oak planks"));
        assertFalse(hit("oak log", "spruce log"));
    }

    @Test
    void aWholePhraseHasToBeContiguous() {
        assertTrue(hit("\"oak log\"", "stripped oak log"));
        assertFalse(hit("\"log oak\"", "oak log"));
        assertFalse(hit("\"oak  log\"", "oak log"));
    }

    @Test
    void alternativesAreOred() {
        assertTrue(hit("iron|gold", "iron axe"));
        assertTrue(hit("iron|gold", "gold axe"));
        assertFalse(hit("iron|gold", "stone axe"));
    }

    @Test
    void anExclusionRejectsWhatContainsItInAnyText() {
        assertFalse(hit("-planks", "oak planks"));
        assertTrue(hit("-planks", "oak log"));
        assertFalse(hit("-planks", "oak", "oak_planks"));
        assertTrue(hit("log -stripped", "oak log"));
        assertFalse(hit("log -stripped", "stripped oak log"));
        assertFalse(hit("-\"oak log\"", "stripped oak log"));
        assertTrue(hit("-\"oak log\"", "oak planks"));
    }

    @Test
    void anExclusionInsideAnAlternativeListReadsLiterally() {
        // "contains iron, or does not contain gold"
        assertTrue(hit("iron|-gold", "iron axe"));
        assertTrue(hit("iron|-gold", "stone axe"));
        assertFalse(hit("iron|-gold", "gold axe"));
    }

    @Test
    void caseDoesNotMatter() {
        assertTrue(hit("PICK", "diamond pickaxe"));
        assertTrue(hit("Pick", "diamond pickaxe"));
    }

    @Test
    void aPlainMatcherKnowsNothingAboutModsAndTagsSoThePrefixesOf044Fail() {
        // 042 treated @ and # as ordinary characters; 044 made them prefixes, and a TextMatcher.ofTexts knows no mods or tags
        assertFalse(hit("@minecraft", "stone"));
        assertFalse(hit("@minecraft", "a@minecraft item"));
        assertFalse(hit("#logs", "oak log"));
        // in the middle of a word they are still ordinary characters
        assertTrue(hit("a@minecraft", "a@minecraft item"));
    }
}
