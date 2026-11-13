package pnnh.mcfloatingbackpack.search;

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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** 043: the contract document, the developer's note and the code say the same thing, and the API stays safe to load anywhere. */
class SearchTermsContractTest {
    private static final Path CONTRACT = Path.of("specs/043-search-terms-provider-api/contracts/search-terms-api.md");
    private static final Path NOTE = Path.of("docs/api/search-terms.md");
    private static final Path API = Path.of("src/main/java/pnnh/mcfloatingbackpack/api");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int tableValue(String document, String name) {
        Matcher m = Pattern.compile("\\|\\s*" + name + "\\s*\\|\\s*(\\d+)\\s*\\|").matcher(document);
        assertTrue(m.find(), name + " is in the contract's numbers table");
        return Integer.parseInt(m.group(1));
    }

    @Test
    void theContractsNumbersTableEqualsTheCodesConstants() throws IOException {
        String contract = read(CONTRACT);
        assertEquals(SearchTermsLimits.MAX_TERMS, tableValue(contract, "MAX_TERMS"));
        assertEquals(SearchTermsLimits.MAX_TERM_LENGTH, tableValue(contract, "MAX_TERM_LENGTH"));
        assertEquals(SearchTermsLimits.MAX_ERRORS, tableValue(contract, "MAX_ERRORS"));
        assertEquals(SearchTermsLimits.CACHE_SIZE, tableValue(contract, "CACHE_SIZE"));
    }

    @Test
    void theProseOfTheContractAndTheNoteNameTheSameNumbers() throws IOException {
        for (Path doc : List.of(CONTRACT, NOTE)) {
            String text = read(doc);
            assertTrue(text.contains("128 字符"), doc + " names the term length limit");
            assertTrue(text.contains("64 个词"), doc + " names the term count limit");
            assertTrue(text.contains("20 次"), doc + " names the error limit");
        }
    }

    @Test
    void theNamesInTheDocumentsAreTheNamesInTheCode() throws IOException {
        String provider = read(API.resolve("SearchTermsProvider.java"));
        String api = read(API.resolve("BackpackSearchApi.java"));
        assertTrue(provider.contains("Collection<String> termsFor(ItemStack stack)"));
        assertTrue(api.contains("public static int apiVersion()"));
        assertTrue(api.contains("public static void registerSearchTermsProvider(String modId, SearchTermsProvider provider)"));
        for (Path doc : List.of(CONTRACT, NOTE)) {
            String text = read(doc);
            assertTrue(text.contains("termsFor(ItemStack stack)"), doc.toString());
            assertTrue(text.contains("apiVersion()"), doc.toString());
            assertTrue(text.contains("registerSearchTermsProvider(String modId, SearchTermsProvider provider)"), doc.toString());
            assertTrue(text.contains("pnnh.mcfloatingbackpack.api"), doc.toString());
        }
    }

    @Test
    void theVersionIsAMethodNeverAConstantTheCallersCompilerCouldInline() throws IOException {
        String api = read(API.resolve("BackpackSearchApi.java"));
        assertFalse(api.contains("public static final int"), "a public constant would be inlined into the caller");
        assertFalse(api.contains("API_VERSION"));
        assertTrue(api.contains("return VERSION;"));
        assertEquals(1, Integer.parseInt(Pattern.compile("private static final int VERSION = (\\d+);").matcher(api).results().findFirst().orElseThrow().group(1)));
    }

    @Test
    void theApiPackageIsOnlyTheTwoTypesAndSafeToLoadOnAServer() throws IOException {
        List<String> files;
        try (Stream<Path> stream = Files.list(API)) {
            files = stream.map(p -> p.getFileName().toString()).sorted().toList();
        }
        assertEquals(List.of("BackpackSearchApi.java", "SearchTermsProvider.java"), files);
        for (String file : files) {
            String source = read(API.resolve(file));
            assertFalse(source.contains("net.neoforged.api.distmarker"), file);
            assertFalse(source.contains("@OnlyIn"), file);
            assertFalse(source.contains("pnnh.mcfloatingbackpack.client"), file);
            assertFalse(source.contains("net.minecraft.client"), file);
        }
    }

    @Test
    void theSearchPackageIsNotClientOnlyEitherSoAModCanRegisterOnAServer() throws IOException {
        Path search = Path.of("src/main/java/pnnh/mcfloatingbackpack/search");
        try (Stream<Path> stream = Files.list(search)) {
            for (Path file : stream.toList()) {
                String source = read(file);
                assertFalse(source.contains("@OnlyIn"), file.toString());
                assertFalse(source.contains("net.minecraft.client"), file.toString());
                assertFalse(source.contains("pnnh.mcfloatingbackpack.client"), file.toString());
            }
        }
    }

    @Test
    void theNeoForgeModsTomlAndTheProtocolAreUntouchedByThisFeature() throws IOException {
        String toml = read(Path.of("src/main/templates/META-INF/neoforge.mods.toml"));
        assertFalse(toml.contains("search"), "no dependency or entry was added for the search terms API");
        String network = read(Path.of("src/main/java/pnnh/mcfloatingbackpack/network/BackpackNetwork.java"));
        assertTrue(network.contains("\"20\""), "the protocol is 20 (054; 048 changed the backpack data on the wire)");
    }

    @Test
    void noSpecificModIsNamedInTheDocumentsOrTheApi() throws IOException {
        // spec Clarifications: this mod only publishes the API; no other mod is recommended, assumed or tested against
        for (Path path : List.of(NOTE, CONTRACT, API.resolve("BackpackSearchApi.java"), API.resolve("SearchTermsProvider.java"))) {
            assertFalse(read(path).toLowerCase().contains("bilingual"), path.toString());
        }
    }
}
