package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.search.TagMatch.Entry;

/** 044: {@code #tag}, the rows of the matching table in contracts/query-syntax.md that are about tags. */
class TagMatchTest {
    private static final List<String> LOG_TAGS = List.of("minecraft:logs", "minecraft:oak_logs", "minecraft:logs_that_burn");
    private static final List<String> IRON_TAGS = List.of("c:ingots", "c:ingots/iron");
    private static final List<Entry> TRASH = List.of(new Entry("trash", "垃圾"));

    // --- item tags ------------------------------------------------------------------------------------------------------

    @Test
    void withoutAColonTheNeedleIsLookedForInThePathOnly() {
        assertTrue(TagMatch.itemTag("logs", LOG_TAGS));
        assertTrue(TagMatch.itemTag("oak_logs", LOG_TAGS));
        assertTrue(TagMatch.itemTag("burn", LOG_TAGS));
        assertFalse(TagMatch.itemTag("minecraft", LOG_TAGS), "the namespace is not part of the path");
        assertFalse(TagMatch.itemTag("planks", LOG_TAGS));
    }

    @Test
    void withAColonTheNeedleMustBeTheStartOfTheWholeId() {
        assertTrue(TagMatch.itemTag("minecraft:logs", LOG_TAGS));
        assertTrue(TagMatch.itemTag("minecraft:", LOG_TAGS));
        assertTrue(TagMatch.itemTag("minecraft:oak", LOG_TAGS));
        assertFalse(TagMatch.itemTag("minecraft:oak_logs_x", LOG_TAGS));
        assertFalse(TagMatch.itemTag("c:logs", LOG_TAGS));
        assertFalse(TagMatch.itemTag("minecraft:ogs", LOG_TAGS), "a colon needle is a prefix, not a substring");
    }

    @Test
    void theCommonTagsOfNeoForgeAreItemTagsLikeAnyOther() {
        assertTrue(TagMatch.itemTag("ingots", IRON_TAGS));
        assertTrue(TagMatch.itemTag("iron", IRON_TAGS));
        assertTrue(TagMatch.itemTag("ingots/iron", IRON_TAGS));
        assertTrue(TagMatch.itemTag("c:ingots", IRON_TAGS));
        assertTrue(TagMatch.itemTag("c:ingots/iron", IRON_TAGS));
        assertTrue(TagMatch.itemTag("c:", IRON_TAGS));
        assertFalse(TagMatch.itemTag("minecraft:ingots", IRON_TAGS));
    }

    @Test
    void anItemWithoutTagsHasNone() {
        assertFalse(TagMatch.itemTag("x", List.of()));
        assertFalse(TagMatch.itemTag("c:", List.of()));
    }

    @Test
    void aTagIdWithoutAColonIsAllPath() {
        // other mods' ids always have a namespace; if one did not, the whole id is its path
        assertTrue(TagMatch.itemTag("odd", List.of("oddtag")));
    }

    // --- the backpack's own tags ----------------------------------------------------------------------------------------

    @Test
    void aBackpackTagIsFoundByItsIdOrItsNameInTheCurrentLanguage() {
        assertTrue(TagMatch.backpackTag("trash", TRASH));
        assertTrue(TagMatch.backpackTag("tra", TRASH));
        assertTrue(TagMatch.backpackTag("垃圾", TRASH));
        assertTrue(TagMatch.backpackTag("垃", TRASH));
        assertFalse(TagMatch.backpackTag("food", TRASH));
    }

    @Test
    void aCustomBackpackTagIsFoundByItsName() {
        List<Entry> custom = List.of(new Entry("my tools", "my tools"));
        assertTrue(TagMatch.backpackTag("my", custom));
        assertTrue(TagMatch.backpackTag("my tools", custom));
        assertFalse(TagMatch.backpackTag("your", custom));
    }

    @Test
    void aNeedleWithAColonNeverFindsABackpackTagBecauseItHasNoNamespace() {
        assertFalse(TagMatch.backpackTag("c:trash", TRASH));
        assertFalse(TagMatch.backpackTag("minecraft:trash", TRASH));
        assertFalse(TagMatch.backpackTag(":trash", TRASH));
    }

    @Test
    void anItemWithoutBackpackTagsHasNone() {
        assertFalse(TagMatch.backpackTag("x", List.of()));
    }

    // --- both, through the grammar --------------------------------------------------------------------------------------

    /** The union the game builds: an item tag or a backpack tag. */
    private static TextMatcher item(List<String> itemTags, List<Entry> backpackTags) {
        return new TextMatcher() {
            @Override
            public boolean contains(String needle) {
                return false;
            }

            @Override
            public boolean hasTag(String needle) {
                return TagMatch.itemTag(needle, itemTags) || TagMatch.backpackTag(needle, backpackTags);
            }
        };
    }

    private static boolean hit(String query, TextMatcher item) {
        return SearchMatcher.matches(SearchQuery.parse(query), item);
    }

    @Test
    void eitherKindOfTagIsEnough() {
        TextMatcher both = item(LOG_TAGS, TRASH);
        TextMatcher itemOnly = item(LOG_TAGS, List.of());
        TextMatcher backpackOnly = item(List.of(), TRASH);
        assertTrue(hit("#logs", both) && hit("#trash", both));
        assertTrue(hit("#logs", itemOnly) && !hit("#trash", itemOnly));
        assertTrue(hit("#trash", backpackOnly) && !hit("#logs", backpackOnly));
        assertTrue(hit("#垃圾", backpackOnly));
    }

    @Test
    void anItemWithoutAnyTagFailsEveryTagQueryAndPassesItsExclusion() {
        TextMatcher none = item(List.of(), List.of());
        assertFalse(hit("#x", none));
        assertFalse(hit("#c:", none));
        assertTrue(hit("-#x", none));
    }

    @Test
    void theGrammarAppliesToTagAlternatives() {
        TextMatcher oakLog = item(LOG_TAGS, List.of());
        TextMatcher planks = item(List.of("minecraft:planks"), List.of());
        assertTrue(hit("#logs|#planks", oakLog));
        assertTrue(hit("#logs|#planks", planks));
        assertFalse(hit("-#planks", planks));
        assertTrue(hit("-#planks", oakLog));
        assertTrue(hit("#minecraft:logs", oakLog));
        assertFalse(hit("#c:logs", oakLog));
        assertTrue(hit("#\"minecraft:logs\"", oakLog));
    }
}
