package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 044: {@code @mod}, the rows of the matching table in contracts/query-syntax.md that are about mods. */
class ModMatchTest {
    private static boolean hit(String query, String namespace, String modName) {
        return SearchMatcher.matches(SearchQuery.parse(query), new TextMatcher() {
            @Override
            public boolean contains(String needle) {
                return false;
            }

            @Override
            public boolean inMod(String needle) {
                return ModMatch.matches(needle, namespace, modName.toLowerCase());
            }
        });
    }

    @Test
    void theNamespaceIsMatchedBySubstringAndCaseDoesNotMatter() {
        assertTrue(hit("@minecraft", "minecraft", "Minecraft"));
        assertTrue(hit("@mine", "minecraft", "Minecraft"));
        assertTrue(hit("@CRAFT", "minecraft", "Minecraft"));
    }

    @Test
    void theDisplayNameIsMatchedToo() {
        assertTrue(hit("@sophisticated", "sophisticatedbackpacks", "Sophisticated Backpacks"));
        assertTrue(hit("@pack", "sophisticatedbackpacks", "Sophisticated Backpacks"));
        // a name that differs from the id
        assertTrue(hit("@traveler", "travelersbackpack", "Traveler's Backpack"));
        assertTrue(hit("@bag", "xyz", "Super Bag Mod"));
    }

    @Test
    void aNameWithASpaceNeedsAQuotedPhrase() {
        assertTrue(hit("@\"sophisticated backpacks\"", "sophisticatedbackpacks", "Sophisticated Backpacks"));
        // unquoted, the blank ends the term: "backpacks" is a second, ordinary word, which this matcher never finds
        assertFalse(hit("@sophisticated backpacks", "sophisticatedbackpacks", "Sophisticated Backpacks"));
    }

    @Test
    void anotherModDoesNotMatch() {
        assertFalse(hit("@minecraft", "sophisticatedbackpacks", "Sophisticated Backpacks"));
        assertFalse(hit("@nosuchmod", "minecraft", "Minecraft"));
    }

    @Test
    void aModWithoutADisplayNameIsOnlyMatchedByItsNamespace() {
        assertTrue(hit("@odd", "oddmod", ""));
        assertFalse(hit("@name", "oddmod", ""));
    }

    @Test
    void theRulesOfTheGrammarApplyToModAlternatives() {
        TextMatcher mekanism = new TextMatcher() {
            @Override
            public boolean contains(String needle) {
                return "iron ingot".contains(needle);
            }

            @Override
            public boolean inMod(String needle) {
                return ModMatch.matches(needle, "mekanism", "mekanism");
            }
        };
        assertTrue(SearchMatcher.matches(SearchQuery.parse("@mekanism ingot"), mekanism));
        assertFalse(SearchMatcher.matches(SearchQuery.parse("@mekanism plank"), mekanism));
        assertFalse(SearchMatcher.matches(SearchQuery.parse("-@mekanism"), mekanism));
        assertTrue(SearchMatcher.matches(SearchQuery.parse("@minecraft|@mekanism"), mekanism));
        assertFalse(SearchMatcher.matches(SearchQuery.parse("@mekanism -ingot"), mekanism));
    }
}
