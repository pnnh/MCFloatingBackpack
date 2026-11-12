package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 044: what no pure test can see because the classes need Minecraft: the two new language keys, the wiring that clears the caches, the
 * tool areas leaving commands alone, and that nothing was added to the network, the configuration or the dependencies.
 */
class SearchPrefixResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path SEARCH = SRC.resolve("client/panel/search");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String value(String file, String key) throws IOException {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(read(LANG.resolve(file)));
        assertTrue(m.find(), file + " has " + key);
        return m.group(1);
    }

    @Test
    void theNewKeysAreInBothLanguages() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            assertFalse(value(file, "panel.mcfloatingbackpack.search.hidden.none").isBlank(), file);
            assertFalse(value(file, "panel.mcfloatingbackpack.search.hidden.nomatch").isBlank(), file);
            String unknown = value(file, "panel.mcfloatingbackpack.search.unknown_command");
            assertTrue(unknown.contains("%s"), file + ": the typed command goes in by position");
            assertTrue(unknown.contains("/hidden"), file + ": says what can be typed");
        }
    }

    @Test
    void theHiddenSummaryReusesThe037KeysAndTheyAreStillThere() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            for (String key : List.of("hidden.title", "hidden.line", "hidden.hint")) {
                assertFalse(value(file, "panel.mcfloatingbackpack." + key).isBlank(), file + " " + key);
            }
        }
        String renderer = read(SEARCH.resolve("SearchRenderer.java"));
        assertTrue(renderer.contains(".hidden.line"));
        assertTrue(renderer.contains(".hidden.title"));
        assertTrue(renderer.contains(".hidden.hint"));
    }

    @Test
    void theCachesAreClearedWhenTheTagsAreSentAgainAndWhenTheResourcesReload() throws IOException {
        String events = read(SEARCH.resolve("SearchFactsEvents.java"));
        assertTrue(events.contains("TagsUpdatedEvent"));
        assertTrue(events.contains("ItemFacts.invalidate()"));
        assertTrue(events.contains("Dist.CLIENT"), "client only");
        String setup = read(SRC.resolve("client/ClientSetup.java"));
        assertTrue(setup.contains("ItemFacts.invalidate()"), "a language switch changes the names of the preset tags");
    }

    @Test
    void theToolAreasFrameNothingForACommand() throws IOException {
        String context = read(SEARCH.resolve("SearchContext.java"));
        int tool = context.indexOf("public static boolean toolMatches");
        assertTrue(tool > 0);
        assertTrue(context.substring(tool).contains("query.command().type() != SearchQuery.Command.Type.NONE"));
    }

    @Test
    void theProvidersWordsOnlyGoIntoTheTextsAndNeverIntoModsOrTags() throws IOException {
        String context = read(SEARCH.resolve("SearchContext.java"));
        String inMod = context.substring(context.indexOf("public boolean inMod"), context.indexOf("public boolean hasTag"));
        String hasTag = context.substring(context.indexOf("public boolean hasTag"));
        assertFalse(inMod.contains("SearchTerms"));
        assertFalse(hasTag.substring(0, hasTag.indexOf("};")).contains("SearchTerms"));
        assertTrue(inMod.contains("ItemFacts.inMod"));
        assertTrue(hasTag.contains("ItemFacts.hasTag"));
    }

    @Test
    void thePureClassesOfThe044HaveNoMinecraftTypes() throws IOException {
        for (String name : new String[] {"ModMatch", "TagMatch", "HiddenSummaryRows", "HiddenSummaryFit", "SearchQuery", "SearchMatcher",
                "SearchResults", "TextMatcher"}) {
            String source = read(SEARCH.resolve(name + ".java"));
            assertFalse(source.contains("net.minecraft"), name);
            assertFalse(source.contains("net.neoforged"), name);
        }
    }

    @Test
    void theProtocolIs18AndTheModsTomlAndTheConfigAreUntouched() throws IOException {
        assertTrue(read(SRC.resolve("network/BackpackNetwork.java")).contains("versioned(\"18\")"));
        String toml = read(Path.of("src/main/templates/META-INF/neoforge.mods.toml"));
        assertFalse(toml.contains("search"));
        assertEquals(1, Pattern.compile("defineListAllowEmpty").matcher(read(SRC.resolve("compat/CompatConfig.java"))).results().count(),
                "no config entry was added: modLevels is still the only list");
    }

    @Test
    void theItemFactsAreClientOnlyAndAreOnlyAskedByTheContext() throws IOException {
        String facts = read(SEARCH.resolve("ItemFacts.java"));
        assertTrue(facts.contains("@OnlyIn(Dist.CLIENT)"));
        try (var files = Files.walk(SRC)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("ItemFacts.java") || name.equals("SearchContext.java") || name.equals("SearchFactsEvents.java")
                        || name.equals("ClientSetup.java")) {
                    continue;
                }
                assertFalse(read(file).contains("ItemFacts."), file + " does not use the facts");
            }
        }
    }
}
