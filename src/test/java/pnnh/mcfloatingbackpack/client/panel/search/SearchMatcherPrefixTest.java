package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 044: how a query with {@code @} and {@code #} is asked of a matcher that knows mods and tags. */
class SearchMatcherPrefixTest {
    /** An item with some texts, a mod name and some tags; the match is a plain substring, as in the game's own rules. */
    private static TextMatcher item(List<String> texts, String mod, Set<String> tags) {
        return new TextMatcher() {
            @Override
            public boolean contains(String needle) {
                return texts.stream().anyMatch(t -> t.contains(needle));
            }

            @Override
            public boolean inMod(String needle) {
                return mod.contains(needle);
            }

            @Override
            public boolean hasTag(String needle) {
                return tags.stream().anyMatch(t -> t.contains(needle));
            }
        };
    }

    private static final TextMatcher OAK_LOG = item(List.of("oak log", "oak_log"), "minecraft", Set.of("logs", "oak_logs"));
    private static final TextMatcher OAK_PLANKS = item(List.of("oak planks", "oak_planks"), "minecraft", Set.of("planks"));
    private static final TextMatcher IRON_INGOT = item(List.of("iron ingot", "iron_ingot"), "mekanism", Set.of("ingots"));

    private static boolean hit(String query, TextMatcher item) {
        return SearchMatcher.matches(SearchQuery.parse(query), item);
    }

    @Test
    void aModAlternativeAsksTheMods() {
        assertTrue(hit("@minecraft", OAK_LOG));
        assertFalse(hit("@minecraft", IRON_INGOT));
        assertTrue(hit("@mekanism", IRON_INGOT));
    }

    @Test
    void aTagAlternativeAsksTheTags() {
        assertTrue(hit("#logs", OAK_LOG));
        assertFalse(hit("#logs", OAK_PLANKS));
        assertTrue(hit("#ingots", IRON_INGOT));
    }

    @Test
    void aWordStillAsksTheTexts() {
        assertTrue(hit("oak", OAK_LOG));
        assertFalse(hit("minecraft", OAK_LOG)); // the mod is not among the texts
        assertFalse(hit("logs", OAK_PLANKS));
    }

    @Test
    void spaceIsAndAcrossKinds() {
        assertTrue(hit("@minecraft #logs", OAK_LOG));
        assertFalse(hit("@minecraft #logs", OAK_PLANKS));
        assertFalse(hit("@mekanism #logs", OAK_LOG));
        assertTrue(hit("@minecraft #logs oak", OAK_LOG));
        assertFalse(hit("@minecraft #logs iron", OAK_LOG));
    }

    @Test
    void aBarIsOrAcrossKinds() {
        assertTrue(hit("#logs|#planks", OAK_LOG));
        assertTrue(hit("#logs|#planks", OAK_PLANKS));
        assertFalse(hit("#logs|#planks", IRON_INGOT));
        assertTrue(hit("@mekanism|#logs", OAK_LOG));
        assertTrue(hit("@mekanism|#logs", IRON_INGOT));
    }

    @Test
    void aDashExcludesWhateverTheKind() {
        assertFalse(hit("-#planks", OAK_PLANKS));
        assertTrue(hit("-#planks", OAK_LOG));
        assertTrue(hit("@minecraft -#planks", OAK_LOG));
        assertFalse(hit("@minecraft -#planks", OAK_PLANKS));
        assertTrue(hit("@minecraft -ingot", OAK_LOG));
        assertFalse(hit("@mekanism -ingot", IRON_INGOT));
    }

    @Test
    void aQuotedPrefixIsAWordAndTheMiddleOfAWordToo() {
        assertFalse(hit("\"@minecraft\"", OAK_LOG)); // the word "@minecraft" is in no text
        assertTrue(hit("\"@minecraft\"", item(List.of("a@minecraft b"), "", Set.of())));
        assertTrue(hit("a@b", item(List.of("a@b"), "", Set.of())));
    }

    @Test
    void aMatcherThatKnowsNothingSaysNoToPrefixesAndYesToTheirExclusions() {
        TextMatcher plain = TextMatcher.ofTexts(List.of("oak log"));
        assertFalse(hit("@minecraft", plain));
        assertFalse(hit("#logs", plain));
        assertTrue(hit("-@minecraft", plain));
        assertTrue(hit("-#logs", plain));
        assertTrue(hit("oak", plain)); // 042's use is untouched
    }

    @Test
    void aCommandsRestOnlyDecidesWhatItsTermsSay() {
        // the matcher does not look at the command: SearchResults/the state do
        assertTrue(hit("/hidden oak", OAK_LOG));
        assertFalse(hit("/hidden iron", OAK_LOG));
        assertTrue(hit("/hidden", OAK_LOG));
    }

    @Test
    void theWordsAProviderAddsAreTextsAndNeverModsOrTags() {
        // 043: a provider's words are among the item's texts; @ and # ask about mods and tags, which they are not
        TextMatcher withProviderWords = item(List.of("oak log", "oak_log", "zsg", "钻石"), "minecraft", Set.of("logs"));
        assertTrue(hit("zsg", withProviderWords));
        assertTrue(hit("钻石", withProviderWords));
        assertFalse(hit("@zsg", withProviderWords));
        assertFalse(hit("#zsg", withProviderWords));
        assertFalse(hit("@钻石", withProviderWords));
        assertTrue(hit("zsg @minecraft #logs", withProviderWords));
    }

    @Test
    void anOrBetweenAPrefixAndAWordMixesBoth() {
        assertTrue(hit("@mekanism|oak", OAK_LOG));
        assertTrue(hit("@mekanism|oak", IRON_INGOT));
        assertFalse(hit("@mekanism|oak", item(List.of("stone"), "minecraft", Set.of())));
    }
}
