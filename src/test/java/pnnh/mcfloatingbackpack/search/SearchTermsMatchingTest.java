package pnnh.mcfloatingbackpack.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.search.SearchMatcher;
import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery;
import pnnh.mcfloatingbackpack.client.panel.search.TextMatcher;

/**
 * 043: what the providers' terms do to 042's search. The matcher is built the way {@code SearchContext.matcherFor} builds it (name and
 * registry path, then the providers' terms); the source-text checks at the end keep that copy honest.
 */
class SearchTermsMatchingTest {
    /** The UI is English: the names the game shows are English, so the Chinese and pinyin forms can only come from a provider. */
    private record Item(String path, String shownName, String chinese, String pinyin, String initials) {
    }

    private static final List<Item> ITEMS = List.of(
            new Item("diamond_pickaxe", "Diamond Pickaxe", "钻石镐", "zuanshigao", "zsg"),
            new Item("golden_apple", "Golden Apple", "金苹果", "jinpingguo", "jpg"),
            new Item("oak_log", "Oak Log", "橡木原木", "xiangmuyuanmu", "xmym"),
            new Item("cobblestone", "Cobblestone", "圆石", "yuanshi", "ys"),
            new Item("bread", "Bread", "面包", "mianbao", "mb"),
            new Item("iron_ingot", "Iron Ingot", "铁锭", "tieding", "td"),
            new Item("redstone", "Redstone", "红石", "hongshi", "hs"),
            new Item("lapis_lazuli", "Lapis Lazuli", "青金石", "qingjinshi", "qjs"),
            new Item("elytra", "Elytra", "鞘翅", "qiaochi", "qc"),
            new Item("netherite_ingot", "Netherite Ingot", "下界合金锭", "xiajiehejinding", "xjhjd"));

    private static ProviderRegistry<String> registry(boolean withProvider) {
        ProviderRegistry<String> registry = new ProviderRegistry<>(s -> s, s -> s, id -> true, new ProviderRegistry.WarnSink() {
            @Override
            public void warn(String message) {
            }

            @Override
            public void info(String message) {
            }

            @Override
            public void debug(String message) {
            }
        });
        if (withProvider) {
            Map<String, List<String>> table = new LinkedHashMap<>();
            for (Item item : ITEMS) {
                // upper case on purpose: the registry lower-cases what a provider gives
                table.put(item.path(), List.of(item.chinese(), item.shownName().toUpperCase(), item.pinyin().toUpperCase(), item.initials().toUpperCase()));
            }
            registry.register("fake", s -> table.get(s));
        }
        return registry;
    }

    /** Built the way SearchContext.matcherFor builds it. */
    private static TextMatcher matcher(ProviderRegistry<String> registry, Item item) {
        List<String> texts = new ArrayList<>();
        texts.add(item.shownName().toLowerCase());
        texts.add(item.path());
        if (registry.hasActive()) {
            texts.addAll(registry.termsFor(item.path()));
        }
        return TextMatcher.ofTexts(texts);
    }

    private static boolean hits(ProviderRegistry<String> registry, Item item, String query) {
        return SearchMatcher.matches(SearchQuery.parse(query), matcher(registry, item));
    }

    private static List<String> hitPaths(ProviderRegistry<String> registry, String query) {
        List<String> out = new ArrayList<>();
        for (Item item : ITEMS) {
            if (hits(registry, item, query)) {
                out.add(item.path());
            }
        }
        return out;
    }

    private static Item item(String path) {
        return ITEMS.stream().filter(i -> i.path().equals(path)).findFirst().orElseThrow();
    }

    @Test
    void everyItemIsFoundByAllFourFormsTheProviderGives() {
        // SC-001: 10 items x (chinese name, english name, full pinyin, initials) = 40 searches, all found
        ProviderRegistry<String> registry = registry(true);
        int found = 0;
        for (Item item : ITEMS) {
            for (String query : new String[] {item.chinese(), item.shownName().toLowerCase(), item.pinyin(), item.initials()}) {
                assertTrue(hits(registry, item, query), item.path() + " not found by " + query);
                found++;
            }
        }
        assertEquals(40, found);
    }

    @Test
    void aProviderTermInUpperCaseIsFoundByLowerCaseAndUpperCaseTyping() {
        ProviderRegistry<String> registry = registry(true);
        assertTrue(hits(registry, item("diamond_pickaxe"), "zsg"));
        assertTrue(hits(registry, item("diamond_pickaxe"), "ZSG"));
    }

    @Test
    void aPartOfAPinyinTermIsFoundToo() {
        ProviderRegistry<String> registry = registry(true);
        assertTrue(hits(registry, item("diamond_pickaxe"), "zuanshi"));
        assertTrue(hits(registry, item("diamond_pickaxe"), "shigao"));
        assertTrue(hits(registry, item("diamond_pickaxe"), "钻石"));
    }

    @Test
    void theInitialsOfOneItemDoNotPickOutTheOthers() {
        ProviderRegistry<String> registry = registry(true);
        assertEquals(List.of("diamond_pickaxe"), hitPaths(registry, "zsg"));
        assertEquals(List.of("golden_apple"), hitPaths(registry, "jpg"));
    }

    @Test
    void theProvidersTermsOnlyAddTheNamesTheGameShowsAndThePathAreStillFound() {
        ProviderRegistry<String> registry = registry(true);
        assertTrue(hits(registry, item("diamond_pickaxe"), "pickaxe"));
        assertTrue(hits(registry, item("diamond_pickaxe"), "diamond_pick"));
        assertTrue(hits(registry, item("diamond_pickaxe"), "diamond pickaxe")); // two words, both found
    }

    @Test
    void withoutAProviderThePinyinAndChineseFindNothing() {
        ProviderRegistry<String> registry = registry(false);
        for (Item item : ITEMS) {
            assertFalse(hits(registry, item, item.pinyin()), item.path());
            assertFalse(hits(registry, item, item.chinese()), item.path());
            assertTrue(hits(registry, item, item.shownName().toLowerCase()), item.path()); // 042's behaviour is untouched
        }
    }

    // --- 042's syntax over the providers' terms ----------------------------------------------------------------------

    @Test
    void spaceIsAndAcrossTheNamesAndTheProvidersTerms() {
        ProviderRegistry<String> registry = registry(true);
        assertEquals(List.of("diamond_pickaxe"), hitPaths(registry, "zs gao"));      // both words are in the pinyin
        assertEquals(List.of("diamond_pickaxe"), hitPaths(registry, "zsg pickaxe")); // one word from the provider, one from the name
        assertEquals(List.of(), hitPaths(registry, "zsg apple"));
    }

    @Test
    void aMinusExcludesWhatTheProvidersTermsFind() {
        ProviderRegistry<String> registry = registry(true);
        List<String> all = hitPaths(registry, "ingot");
        assertEquals(List.of("iron_ingot", "netherite_ingot"), all);
        assertEquals(List.of("netherite_ingot"), hitPaths(registry, "ingot -td"));
    }

    @Test
    void aBarMeansOrAcrossTheProvidersTerms() {
        ProviderRegistry<String> registry = registry(true);
        assertEquals(List.of("diamond_pickaxe", "golden_apple"), hitPaths(registry, "zsg|jpg"));
    }

    @Test
    void aQuotedPhraseIsOneTermAndASpaceInsideOnlyMatchesATermThatHasIt() {
        ProviderRegistry<String> registry = registry(true);
        assertEquals(List.of("diamond_pickaxe"), hitPaths(registry, "\"diamond pickaxe\"")); // the shown name has the space
        assertEquals(List.of(), hitPaths(registry, "\"zuan shi\""));                          // no term has a space there
        assertEquals(List.of("diamond_pickaxe"), hitPaths(registry, "\"zuanshi\""));
    }

    // --- nothing registered: nothing is asked -------------------------------------------------------------------------

    @Test
    void withoutProvidersNothingIsAskedAndTheTextsAreTheOldTwo() {
        ProviderRegistry<String> registry = registry(false);
        assertFalse(registry.hasActive());
        assertEquals(List.of(), registry.termsFor("diamond_pickaxe"));
        // the matcher of an item is then 042's: the shown name and the path, nothing else
        assertTrue(matcher(registry, item("bread")).contains("bread"));
        assertFalse(matcher(registry, item("bread")).contains("mianbao"));
    }

    // --- the copy in this test is the real code -----------------------------------------------------------------------

    @Test
    void searchContextBuildsItsTextsTheWayThisTestDoes() throws IOException {
        String source = Files.readString(Path.of("src/main/java/pnnh/mcfloatingbackpack/client/panel/search/SearchContext.java"), StandardCharsets.UTF_8);
        // 042's two texts, still built from the display name and the registry path
        assertTrue(source.contains("stack.getHoverName().getString().toLowerCase(Locale.ROOT)"));
        assertTrue(source.contains("BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT)"));
        // with no provider asked: exactly 042's list
        int guard = source.indexOf("if (!SearchTerms.hasActive())");
        assertTrue(guard > 0, "matcherFor asks hasActive() first");
        assertTrue(source.indexOf("TextMatcher.ofTexts(List.of(name, path))", guard) > guard);
        // with providers: the terms come after the name and the path
        assertTrue(source.contains("SearchTerms.termsFor(stack)"));
        int nameAt = source.indexOf("texts.add(name)");
        int pathAt = source.indexOf("texts.add(path)");
        int extraAt = source.indexOf("texts.addAll(extra)");
        assertTrue(nameAt > 0 && pathAt > nameAt && extraAt > pathAt);
        // the toolMatches path goes through matcherFor, so it sees the providers too
        assertTrue(source.contains("SearchMatcher.matches(query, matcherFor(stack))"));
    }
}
