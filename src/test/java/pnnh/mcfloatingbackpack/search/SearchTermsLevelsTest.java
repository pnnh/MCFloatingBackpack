package pnnh.mcfloatingbackpack.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.CompatPolicy;

/** 043: the {@code modLevels} setting decides whether a provider is used: only FULL (or not listed) uses it. */
class SearchTermsLevelsTest {
    private static CompatPolicy policy(String... entries) {
        return CompatPolicy.parse(List.of(entries)).policy();
    }

    /** A registry that asks a (replaceable) policy the way SearchTerms does: only FULL uses a provider. */
    private static ProviderRegistry<String> registryOver(AtomicReference<CompatPolicy> policy, AtomicInteger calls) {
        ProviderRegistry<String> registry = new ProviderRegistry<>(s -> s, s -> s, id -> policy.get().specificAdaptation(id), new ProviderRegistry.WarnSink() {
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
            return List.of("zsg");
        });
        return registry;
    }

    @Test
    void aModThatIsNotListedUsesItsProvider() {
        AtomicInteger calls = new AtomicInteger();
        ProviderRegistry<String> registry = registryOver(new AtomicReference<>(policy()), calls);
        assertTrue(registry.hasActive());
        assertEquals(List.of("zsg"), registry.termsFor("item"));
    }

    @Test
    void fullUsesItsProvider() {
        AtomicInteger calls = new AtomicInteger();
        ProviderRegistry<String> registry = registryOver(new AtomicReference<>(policy("x=FULL")), calls);
        assertEquals(List.of("zsg"), registry.termsFor("item"));
    }

    @Test
    void genericAndOffBothIgnoreItAndNeverCallIt() {
        for (String level : new String[] {"GENERIC", "OFF"}) {
            AtomicInteger calls = new AtomicInteger();
            ProviderRegistry<String> registry = registryOver(new AtomicReference<>(policy("x=" + level)), calls);
            assertFalse(registry.hasActive(), level);
            assertEquals(List.of(), registry.termsFor("item"), level);
            assertEquals(0, calls.get(), level);
        }
    }

    @Test
    void anotherModsLevelDoesNotTouchIt() {
        AtomicInteger calls = new AtomicInteger();
        ProviderRegistry<String> registry = registryOver(new AtomicReference<>(policy("jei=OFF", "other=GENERIC")), calls);
        assertEquals(List.of("zsg"), registry.termsFor("item"));
    }

    @Test
    void aChangedSettingWorksFromTheNextAskWithoutClearingTheCache() {
        AtomicReference<CompatPolicy> current = new AtomicReference<>(policy());
        AtomicInteger calls = new AtomicInteger();
        ProviderRegistry<String> registry = registryOver(current, calls);
        assertEquals(List.of("zsg"), registry.termsFor("item"));
        assertEquals(1, calls.get());

        current.set(policy("x=OFF"));
        assertEquals(List.of(), registry.termsFor("item"));
        assertEquals(List.of(), registry.termsFor("other"));
        assertEquals(1, calls.get());

        current.set(policy("x=FULL"));
        assertEquals(List.of("zsg"), registry.termsFor("item"));
        assertEquals(1, calls.get()); // came back from the cache: the setting does not clear it
        assertEquals(List.of("zsg"), registry.termsFor("other"));
        assertEquals(2, calls.get());
    }

    @Test
    void searchTermsAsksTheRealPolicyAndOnlyFullCounts() throws IOException {
        String source = Files.readString(Path.of("src/main/java/pnnh/mcfloatingbackpack/search/SearchTerms.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("CompatConfig.policy().specificAdaptation(modId)"));
    }

    @Test
    void theConfigAndBothLanguagesSayWhatGenericAndOffMeanForSearchTerms() throws IOException {
        Path lang = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
        String en = Files.readString(lang.resolve("en_us.json"), StandardCharsets.UTF_8);
        String zh = Files.readString(lang.resolve("zh_cn.json"), StandardCharsets.UTF_8);
        String config = Files.readString(Path.of("src/main/java/pnnh/mcfloatingbackpack/compat/CompatConfig.java"), StandardCharsets.UTF_8);
        assertTrue(en.contains("search terms are ignored"));
        assertTrue(zh.contains("忽略它提供的搜索词"));
        assertTrue(config.contains("search terms are ignored"));
    }

    @Test
    void aModIdMustLookLikeOneSoTheSettingCanNameIt() {
        assertTrue(ProviderIds.isValid("examplemod"));
        assertTrue(ProviderIds.isValid("my_mod_2"));
        assertFalse(ProviderIds.isValid(null));
        assertFalse(ProviderIds.isValid(""));
        assertFalse(ProviderIds.isValid("My-Mod"));
        assertFalse(ProviderIds.isValid("with space"));
        assertFalse(ProviderIds.isValid("a=b"));
    }
}
