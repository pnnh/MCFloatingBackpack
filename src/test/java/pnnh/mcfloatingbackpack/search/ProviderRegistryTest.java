package pnnh.mcfloatingbackpack.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 043: the registry, with strings standing in for item stacks. */
class ProviderRegistryTest {
    private final List<String> warnings = new ArrayList<>();
    private final List<String> infos = new ArrayList<>();
    private final List<String> debugs = new ArrayList<>();
    private final Set<String> disabledMods = new HashSet<>();
    private ProviderRegistry<String> registry;

    @BeforeEach
    void setUp() {
        registry = new ProviderRegistry<>(s -> s, s -> s, id -> !disabledMods.contains(id), new ProviderRegistry.WarnSink() {
            @Override
            public void warn(String message) {
                warnings.add(message);
            }

            @Override
            public void info(String message) {
                infos.add(message);
            }

            @Override
            public void debug(String message) {
                debugs.add(message);
            }
        });
    }

    /** A provider that counts its calls and answers from a function. */
    private static final class Counting implements Function<String, Collection<String>> {
        final AtomicInteger calls = new AtomicInteger();
        final Function<String, Collection<String>> answer;

        Counting(Function<String, Collection<String>> answer) {
            this.answer = answer;
        }

        @Override
        public Collection<String> apply(String subject) {
            calls.incrementAndGet();
            return answer.apply(subject);
        }
    }

    // --- registering and asking ------------------------------------------------------------------------------------

    @Test
    void aRegisteredProvidersTermsAreGivenLowerCased() {
        registry.register("a", s -> List.of("ZSG", "Zuanshigao"));
        assertEquals(List.of("zsg", "zuanshigao"), registry.termsFor("diamond_pickaxe"));
    }

    @Test
    void theProviderIsAskedAboutTheSubject() {
        registry.register("a", s -> s.equals("pick") ? List.of("zsg") : List.of());
        assertEquals(List.of("zsg"), registry.termsFor("pick"));
        assertEquals(List.of(), registry.termsFor("log"));
    }

    @Test
    void severalProvidersGiveTheUnion() {
        registry.register("a", s -> List.of("zsg", "common"));
        registry.register("b", s -> List.of("diamond", "common"));
        List<String> terms = registry.termsFor("x");
        assertEquals(Set.of("zsg", "diamond", "common"), new HashSet<>(terms));
        assertEquals(3, terms.size());
    }

    @Test
    void registeringIsLoggedOnceAtInfoAndReplacingAtDebug() {
        registry.register("a", s -> List.of("x"));
        assertEquals(1, infos.size());
        assertTrue(infos.get(0).contains("'a'"));
        registry.register("a", s -> List.of("y"));
        assertEquals(1, infos.size());
        assertEquals(1, debugs.size());
        assertEquals(List.of(), warnings);
    }

    @Test
    void registeringAgainForTheSameModReplacesTheProviderAndItsCache() {
        Counting first = new Counting(s -> List.of("old"));
        Counting second = new Counting(s -> List.of("new"));
        registry.register("a", first);
        assertEquals(List.of("old"), registry.termsFor("x"));
        registry.register("a", second);
        assertEquals(List.of("new"), registry.termsFor("x"));
        assertEquals(1, first.calls.get());
        assertEquals(1, second.calls.get());
    }

    @Test
    void aProviderRegisteredAfterTheFirstSearchIsUsedFromTheNextOne() {
        assertFalse(registry.hasActive());
        assertEquals(List.of(), registry.termsFor("x"));
        registry.register("late", s -> List.of("zsg"));
        assertTrue(registry.hasActive());
        assertEquals(List.of("zsg"), registry.termsFor("x"));
    }

    // --- nothing registered: exactly the old behaviour -----------------------------------------------------------

    @Test
    void withoutProvidersNothingIsActiveAndNothingIsLogged() {
        assertFalse(registry.hasActive());
        assertEquals(List.of(), registry.termsFor("x"));
        assertEquals(List.of(), warnings);
        assertEquals(List.of(), infos);
        assertEquals(List.of(), debugs);
    }

    @Test
    void whenEveryProviderIsSwitchedOffNothingIsActiveAndNoneIsAsked() {
        Counting provider = new Counting(s -> List.of("zsg"));
        registry.register("a", provider);
        disabledMods.add("a");
        assertFalse(registry.hasActive());
        assertEquals(List.of(), registry.termsFor("x"));
        assertEquals(0, provider.calls.get());
    }

    // --- the cache -----------------------------------------------------------------------------------------------

    @Test
    void oneSubjectCostsOneCallPerCacheGeneration() {
        Counting provider = new Counting(s -> List.of("zsg"));
        registry.register("a", provider);
        for (int i = 0; i < 50; i++) {
            registry.termsFor("pick");
        }
        assertEquals(1, provider.calls.get());
        registry.termsFor("log");
        assertEquals(2, provider.calls.get());
    }

    @Test
    void differentKeysAreAskedSeparately() {
        // the production key is item + components; here the key is the whole string, so "potion:a" and "potion:b" differ
        Counting provider = new Counting(s -> List.of(s + "-name"));
        registry.register("a", provider);
        assertEquals(List.of("potion:a-name"), registry.termsFor("potion:a"));
        assertEquals(List.of("potion:b-name"), registry.termsFor("potion:b"));
        assertEquals(2, provider.calls.get());
    }

    @Test
    void clearCachesMakesTheNextAskCallTheProviderAgain() {
        Counting provider = new Counting(s -> List.of("zsg"));
        registry.register("a", provider);
        registry.termsFor("pick");
        registry.clearCaches();
        registry.termsFor("pick");
        assertEquals(2, provider.calls.get());
    }

    @Test
    void theCacheIsBounded() {
        Counting provider = new Counting(s -> List.of("t"));
        registry.register("a", provider);
        for (int i = 0; i < SearchTermsLimits.CACHE_SIZE + 10; i++) {
            registry.termsFor("item" + i);
        }
        int before = provider.calls.get();
        registry.termsFor("item0"); // long evicted: asked again
        assertEquals(before + 1, provider.calls.get());
        registry.termsFor("item" + (SearchTermsLimits.CACHE_SIZE + 9)); // newest: still cached
        assertEquals(before + 1, provider.calls.get());
    }

    // --- modLevels (the enabled predicate) -----------------------------------------------------------------------

    @Test
    void aSwitchedOffProviderIsNotCalledAndComesBackWithoutLosingItsCache() {
        Counting provider = new Counting(s -> List.of("zsg"));
        registry.register("a", provider);
        assertEquals(List.of("zsg"), registry.termsFor("x"));
        assertEquals(1, provider.calls.get());
        disabledMods.add("a");
        assertEquals(List.of(), registry.termsFor("x"));
        assertEquals(List.of(), registry.termsFor("y"));
        assertEquals(1, provider.calls.get());
        disabledMods.remove("a");
        assertEquals(List.of("zsg"), registry.termsFor("x"));
        assertEquals(1, provider.calls.get()); // still cached, not cleared by the setting
    }

    @Test
    void onlyTheSwitchedOffModIsLeftOut() {
        registry.register("a", s -> List.of("from-a"));
        registry.register("b", s -> List.of("from-b"));
        disabledMods.add("a");
        assertEquals(List.of("from-b"), registry.termsFor("x"));
        assertTrue(registry.hasActive());
    }

    // --- a provider that misbehaves --------------------------------------------------------------------------------

    @Test
    void anExceptionCostsOnlyThatItemAndOtherProvidersAreUnaffected() {
        registry.register("bad", s -> {
            throw new IllegalStateException("boom");
        });
        registry.register("good", s -> List.of("zsg"));
        assertEquals(List.of("zsg"), registry.termsFor("x"));
    }

    @Test
    void anExceptionIsWarnedAboutOncePerClassWithTheModIdAndTheType() {
        registry.register("bad", s -> {
            throw new IllegalStateException("boom");
        });
        for (int i = 0; i < 10; i++) {
            registry.termsFor("item" + i);
        }
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("'bad'"), warnings.get(0));
        assertTrue(warnings.get(0).contains("IllegalStateException"), warnings.get(0));
    }

    @Test
    void aDifferentExceptionClassIsWarnedAboutAgain() {
        registry.register("bad", s -> {
            if (s.startsWith("n")) {
                throw new NullPointerException();
            }
            throw new IllegalStateException();
        });
        registry.termsFor("a1");
        registry.termsFor("a2");
        registry.termsFor("n1");
        assertEquals(2, warnings.size());
    }

    @Test
    void aFailingItemIsNotAskedAgainEveryFrame() {
        Counting provider = new Counting(s -> {
            throw new IllegalStateException("boom");
        });
        registry.register("bad", provider);
        for (int i = 0; i < 100; i++) {
            registry.termsFor("same-item");
        }
        assertEquals(1, provider.calls.get());
        assertEquals(1, warnings.size());
    }

    @Test
    void afterTooManyErrorsTheProviderIsSwitchedOffAndNotAskedAgain() {
        Counting provider = new Counting(s -> {
            throw new IllegalStateException("boom");
        });
        registry.register("bad", provider);
        for (int i = 0; i < SearchTermsLimits.MAX_ERRORS + 10; i++) {
            registry.termsFor("item" + i);
        }
        assertEquals(SearchTermsLimits.MAX_ERRORS, provider.calls.get());
        assertEquals(2, warnings.size()); // first of the class, then "switched off"
        assertTrue(warnings.get(1).contains("switched off"), warnings.get(1));
        assertFalse(registry.hasActive());
    }

    @Test
    void aSwitchedOffProviderStaysOffAfterTheCachesAreCleared() {
        Counting provider = new Counting(s -> {
            throw new IllegalStateException("boom");
        });
        registry.register("bad", provider);
        for (int i = 0; i < SearchTermsLimits.MAX_ERRORS; i++) {
            registry.termsFor("item" + i);
        }
        registry.clearCaches();
        registry.termsFor("another");
        assertEquals(SearchTermsLimits.MAX_ERRORS, provider.calls.get());
    }

    @Test
    void aLinkageErrorFromAProviderIsContainedToo() {
        registry.register("bad", s -> {
            throw new NoClassDefFoundError("some/Missing");
        });
        registry.register("good", s -> List.of("zsg"));
        assertEquals(List.of("zsg"), registry.termsFor("x"));
        assertEquals(1, warnings.size());
    }

    @Test
    void nullAndEmptyAnswersAreNoTermsAndNoErrors() {
        registry.register("a", s -> null);
        registry.register("b", s -> List.of());
        assertEquals(List.of(), registry.termsFor("x"));
        assertEquals(List.of(), warnings);
        assertTrue(registry.hasActive());
    }

    @Test
    void blankTermsAreDroppedAndTooLongTermsAreWarnedAboutOncePerProvider() {
        registry.register("a", s -> Arrays.asList("zsg", "", "  ", null, "x".repeat(SearchTermsLimits.MAX_TERM_LENGTH + 1)));
        assertEquals(List.of("zsg"), registry.termsFor("item1"));
        assertEquals(List.of("zsg"), registry.termsFor("item2"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("'a'") && warnings.get(0).contains("longer"), warnings.get(0));
    }

    @Test
    void tooManyTermsAreCutAndWarnedAboutOnceAndNeverSwitchTheProviderOff() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < SearchTermsLimits.MAX_TERMS + 5; i++) {
            many.add("t" + i);
        }
        registry.register("a", s -> many);
        for (int i = 0; i < SearchTermsLimits.MAX_ERRORS * 3; i++) {
            assertEquals(SearchTermsLimits.MAX_TERMS, registry.termsFor("item" + i).size());
        }
        assertEquals(1, warnings.size());
        assertTrue(registry.hasActive());
    }

    @Test
    void theProviderIsGivenWhatPrepareMakesOfTheSubject() {
        List<String> seen = new ArrayList<>();
        ProviderRegistry<String> preparing = new ProviderRegistry<>(s -> s, s -> s + "!", id -> true, new ProviderRegistry.WarnSink() {
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
        preparing.register("a", s -> {
            seen.add(s);
            return List.of("t");
        });
        preparing.termsFor("pick");
        assertEquals(List.of("pick!"), seen);
    }
}
