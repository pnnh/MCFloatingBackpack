package pnnh.mcfloatingbackpack.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.search.SearchMatcher;
import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery;
import pnnh.mcfloatingbackpack.client.panel.search.TextMatcher;

/**
 * 043 SC-003: a rough cost of one query over 400 cells with a provider of 5 terms, asked cold and warm. The bounds are generous (a CI
 * machine is slow and noisy); the measured numbers are printed and written into DELIVERY. This cannot measure the cost of hashing big
 * data components (it needs Minecraft's classes).
 */
class SearchTermsCostTest {
    private static final int CELLS = 400;

    @Test
    void oneQueryOverFourHundredCellsIsCheapColdAndWarm() {
        AtomicInteger calls = new AtomicInteger();
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
        registry.register("x", s -> {
            calls.incrementAndGet();
            return List.of("zuanshigao", "zsg", "diamond pickaxe", "钻石镐", "extra");
        });
        SearchQuery query = SearchQuery.parse("zsg -nothing");

        // warm up the JIT with a different set of keys, so the cold run below is cold for the cache only
        for (int round = 0; round < 200; round++) {
            for (int i = 0; i < CELLS; i++) {
                SearchMatcher.matches(query, TextMatcher.ofTexts(List.of("name" + i, "path" + i)));
            }
        }

        long coldStart = System.nanoTime();
        int cold = run(registry, query);
        long coldNanos = System.nanoTime() - coldStart;
        assertEquals(CELLS, calls.get(), "every cell asked once when cold");

        long warmStart = System.nanoTime();
        int warm = 0;
        int rounds = 100;
        for (int round = 0; round < rounds; round++) {
            warm = run(registry, query);
        }
        long warmNanos = (System.nanoTime() - warmStart) / rounds;
        assertEquals(CELLS, calls.get(), "warm queries never ask the provider again");
        assertEquals(CELLS, cold);
        assertEquals(CELLS, warm);

        System.out.println("SC-003 cost: 400 cells, 5 terms: cold " + coldNanos / 1000 + " us, warm " + warmNanos / 1000 + " us per query");
        assertTrue(coldNanos < 200_000_000L, "cold query under 200 ms even on a slow machine, was " + coldNanos / 1000 + " us");
        assertTrue(warmNanos < 50_000_000L, "warm query under 50 ms even on a slow machine, was " + warmNanos / 1000 + " us");
    }

    private static int run(ProviderRegistry<String> registry, SearchQuery query) {
        int hits = 0;
        for (int i = 0; i < CELLS; i++) {
            List<String> texts = new java.util.ArrayList<>();
            texts.add("name" + i);
            texts.add("path" + i);
            texts.addAll(registry.termsFor("item" + i));
            if (SearchMatcher.matches(query, TextMatcher.ofTexts(texts))) {
                hits++;
            }
        }
        return hits;
    }
}
