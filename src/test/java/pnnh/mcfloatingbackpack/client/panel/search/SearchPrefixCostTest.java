package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 044 SC-004: a rough cost of one query with {@code @} and {@code #} over 400 cells, once with the per-item-kind facts still to be
 * worked out (cold) and once from the cache (warm). The bounds are generous (a CI machine is slow and noisy); the measured numbers are
 * printed and written into DELIVERY. The in-game cost of working out the facts of an item kind ({@code getTags()}, the mod list) needs
 * Minecraft's classes and is not measured here.
 */
class SearchPrefixCostTest {
    private static final int CELLS = 400;
    private static final int KINDS = 150;

    private record Facts(String namespace, String modName, List<String> tags, List<TagMatch.Entry> backpackTags) {
    }

    private static Facts work(int kind) {
        List<String> tags = new ArrayList<>();
        for (int t = 0; t < 12; t++) {
            tags.add((t % 3 == 0 ? "c" : "minecraft") + ":group" + (kind % 7) + "_" + t + "/sub" + t);
        }
        return new Facts(kind % 5 == 0 ? "minecraft" : "mod" + kind % 9, "Mod Number " + kind % 9, List.copyOf(tags),
                List.of(new TagMatch.Entry("tag" + kind % 4, "标签" + kind % 4)));
    }

    private static int run(Map<Integer, Facts> cache, SearchQuery query) {
        int hits = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            int kind = cell % KINDS;
            TextMatcher matcher = new TextMatcher() {
                @Override
                public boolean contains(String needle) {
                    return ("item" + kind).contains(needle);
                }

                @Override
                public boolean inMod(String needle) {
                    Facts f = cache.computeIfAbsent(kind, SearchPrefixCostTest::work);
                    return ModMatch.matches(needle, f.namespace(), f.modName().toLowerCase());
                }

                @Override
                public boolean hasTag(String needle) {
                    Facts f = cache.computeIfAbsent(kind, SearchPrefixCostTest::work);
                    return TagMatch.itemTag(needle, f.tags()) || TagMatch.backpackTag(needle, f.backpackTags());
                }
            };
            if (SearchMatcher.matches(query, matcher)) {
                hits++;
            }
        }
        return hits;
    }

    @Test
    void oneQueryWithPrefixesOverFourHundredCellsIsCheapColdAndWarm() {
        SearchQuery query = SearchQuery.parse("@minecraft #group3 -#nothing item");
        // warm up the JIT on other facts so that the cold run is cold for the cache only
        for (int round = 0; round < 100; round++) {
            run(new HashMap<>(), SearchQuery.parse("@mod #sub item"));
        }

        Map<Integer, Facts> cache = new HashMap<>();
        long coldStart = System.nanoTime();
        int cold = run(cache, query);
        long coldNanos = System.nanoTime() - coldStart;
        assertEquals(KINDS, cache.size(), "facts worked out once per item kind");

        int rounds = 100;
        long warmStart = System.nanoTime();
        int warm = 0;
        for (int round = 0; round < rounds; round++) {
            warm = run(cache, query);
        }
        long warmNanos = (System.nanoTime() - warmStart) / rounds;
        assertEquals(cold, warm);
        assertEquals(KINDS, cache.size());

        System.out.println("SC-004 cost: 400 cells, 150 item kinds, 12 tags each: cold " + coldNanos / 1000 + " us, warm " + warmNanos / 1000
                + " us per query");
        assertTrue(coldNanos < 200_000_000L, "cold query under 200 ms even on a slow machine, was " + coldNanos / 1000 + " us");
        assertTrue(warmNanos < 50_000_000L, "warm query under 50 ms even on a slow machine, was " + warmNanos / 1000 + " us");
    }
}
