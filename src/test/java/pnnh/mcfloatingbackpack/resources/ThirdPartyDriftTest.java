package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.Requirement;
import pnnh.mcfloatingbackpack.compat.ThirdPartyCompat;
import pnnh.mcfloatingbackpack.compat.ThirdPartyTable;
import pnnh.mcfloatingbackpack.compat.ThirdPartyVersion;
import pnnh.mcfloatingbackpack.compat.VersionRange;

/**
 * 058: the table of verified versions ({@link ThirdPartyTable}) is the one source of truth, and three other places must not drift from it:
 * the mods.toml (it must keep saying {@code [0,)}, because FML refuses to load the game when an installed optional dependency is outside
 * its range), the README's table, and the versions in build.gradle that the mod is compiled and tried with. Each check has a counter-example
 * to show it does fail.
 */
class ThirdPartyDriftTest {
    /** The Modrinth project of each mod, as build.gradle names it. */
    private static final Map<String, String> SLUG = Map.of(
            "integratedterminals", "integrated-terminals",
            "integrateddynamics", "integrated-dynamics",
            "commoncapabilities", "common-capabilities");

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    /** Every mod the table names (the rows and what they need), and Cyclops Core, which the classes of Integrated Terminals extend. */
    private static Set<String> modIdsInTable() {
        Set<String> ids = new LinkedHashSet<>();
        for (ThirdPartyCompat row : ThirdPartyTable.ALL) {
            ids.add(row.modId());
            row.also().forEach(required -> ids.add(required.modId()));
        }
        ids.add("cyclopscore");
        return ids;
    }

    // --- neoforge.mods.toml ------------------------------------------------------------------------------------

    static List<String> checkModsToml(String toml, Set<String> modIds) {
        Map<String, String[]> declared = new LinkedHashMap<>();
        for (String block : toml.split("\\[\\[dependencies\\.")) {
            Matcher id = Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"([^\"]+)\"").matcher(block);
            if (!id.find()) {
                continue;
            }
            Matcher type = Pattern.compile("(?m)^\\s*type\\s*=\\s*\"([^\"]*)\"").matcher(block);
            Matcher range = Pattern.compile("(?m)^\\s*versionRange\\s*=\\s*\"([^\"]*)\"").matcher(block);
            declared.put(id.group(1), new String[] {type.find() ? type.group(1) : null, range.find() ? range.group(1) : null});
        }
        List<String> problems = new ArrayList<>();
        for (String modId : modIds) {
            String[] block = declared.get(modId);
            if (block == null) {
                problems.add(modId + ": no dependency block in neoforge.mods.toml");
            } else {
                if (!"optional".equals(block[0])) {
                    problems.add(modId + ": must be type=\"optional\" but is " + block[0]);
                }
                if (!"[0,)".equals(block[1])) {
                    problems.add(modId + ": versionRange must be \"[0,)\" (the real range is in ThirdPartyTable) but is " + block[1]);
                }
            }
        }
        return problems;
    }

    @Test
    void theModsTomlDeclaresEveryTableModAsOptionalWithAnOpenRange() throws IOException {
        assertEquals(List.of(), checkModsToml(read("src/main/templates/META-INF/neoforge.mods.toml"), modIdsInTable()));
    }

    @Test
    void theModsTomlCheckFailsOnANarrowRangeAMissingBlockAndARequiredDependency() {
        String ok = "[[dependencies.${mod_id}]]\n modId=\"a\"\n type=\"optional\"\n versionRange=\"[0,)\"\n";
        assertEquals(List.of(), checkModsToml(ok, Set.of("a")));
        assertEquals(1, checkModsToml(ok.replace("[0,)", "[1.11,1.12)"), Set.of("a")).size());
        assertEquals(1, checkModsToml(ok.replace("[0,)", "[1,)"), Set.of("a")).size());
        assertEquals(1, checkModsToml(ok.replace("optional", "required"), Set.of("a")).size());
        assertEquals(1, checkModsToml(ok, Set.of("a", "missing")).size());
    }

    // --- README ------------------------------------------------------------------------------------------------

    static List<String> checkReadme(String name, String text) {
        List<String> problems = new ArrayList<>();
        for (ThirdPartyCompat row : ThirdPartyTable.ALL) {
            if (!text.contains(row.displayName())) {
                problems.add(name + ": the table has no row for " + row.displayName());
            }
            for (VersionRange range : row.supported()) {
                if (!text.contains(range.toString())) {
                    problems.add(name + ": the verified versions of " + row.displayName() + " do not mention " + range);
                }
            }
            for (Requirement required : row.also()) {
                for (VersionRange range : required.supported()) {
                    if (!text.contains(range.toString())) {
                        problems.add(name + ": the verified versions of " + required.displayName() + " (needed by " + row.displayName()
                                + ") do not mention " + range);
                    }
                }
            }
        }
        return problems;
    }

    @Test
    void bothReadmesHoldTheVerifiedVersionsOfTheTable() throws IOException {
        assertEquals(List.of(), checkReadme("README.md", read("README.md")));
        assertEquals(List.of(), checkReadme("README_CN.md", read("README_CN.md")));
    }

    @Test
    void theReadmeCheckFailsWhenARangeOrARowIsMissing() {
        assertEquals(4, checkReadme("x", "nothing").size());
        assertEquals(List.of(), checkReadme("x", "Integrated Terminals [1.11.0, 1.12.0) [1.39.0, 1.40.0) [2.11.0, 2.12.0)"));
        assertEquals(1, checkReadme("x", "Integrated Terminals [1.11.0, 1.13.0) [1.39.0, 1.40.0) [2.11.0, 2.12.0)").size());
    }

    @Test
    void theReadmesDescribeTheServersPerModLevels() throws IOException {
        String en = read("README.md");
        String cn = read("README_CN.md");
        assertTrue(en.contains("serverconfig/mcfloatingbackpack-server.toml") && en.contains("**stricter**") && en.contains("once for each screen they open"));
        assertTrue(cn.contains("serverconfig/mcfloatingbackpack-server.toml") && cn.contains("**更严**") && cn.contains("每打开一次界面"));
        assertFalse(en.contains("cannot be switched off separately yet") || en.contains("are a **client** setting"));
        assertFalse(cn.contains("暂时还不能单独关闭") || cn.contains("是**客户端**配置"));
        assertTrue(en.contains("allowUntestedVersions") && en.contains("back up the world"));
        assertTrue(cn.contains("allowUntestedVersions") && cn.contains("备份存档"));
    }

    // --- build.gradle ------------------------------------------------------------------------------------------

    private static final Pattern GRADLE_LINE =
            Pattern.compile("(?m)^\\s*(compileOnly|localRuntime)\\(?\\s*\"maven\\.modrinth:([a-z-]+):([^\"]+)\"(\\)\\s*\\{\\s*transitive\\s*=\\s*false\\s*\\})?");

    /** The version of a gradle coordinate such as {@code 1.21.1-1.11.2}: the Minecraft version in front is not the mod's. */
    private static Optional<ThirdPartyVersion> modVersion(String coordinateVersion) {
        return ThirdPartyVersion.parse(coordinateVersion.startsWith("1.21.1-") ? coordinateVersion.substring("1.21.1-".length()) : coordinateVersion);
    }

    static List<String> checkGradle(String gradle) {
        Map<String, String> compileOnly = new LinkedHashMap<>();
        Map<String, String> localRuntime = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        Matcher m = GRADLE_LINE.matcher(gradle);
        while (m.find()) {
            Map<String, String> into = m.group(1).equals("compileOnly") ? compileOnly : localRuntime;
            into.put(m.group(2), m.group(3));
            if (m.group(1).equals("compileOnly") && (m.group(2).startsWith("integrated-") || m.group(2).equals("common-capabilities")
                    || m.group(2).equals("cyclops-core")) && m.group(4) == null) {
                problems.add("compileOnly " + m.group(2) + " needs { transitive = false }");
            }
        }
        for (String slug : List.of("integrated-terminals", "integrated-dynamics", "common-capabilities", "cyclops-core")) {
            if (!compileOnly.containsKey(slug)) {
                problems.add("no compileOnly line for " + slug);
            }
        }
        for (Map.Entry<String, String> mod : SLUG.entrySet()) {
            String slug = mod.getValue();
            String version = localRuntime.get(slug);
            if (version == null) {
                problems.add("no localRuntime line for " + slug);
                continue;
            }
            if (!version.equals(compileOnly.get(slug))) {
                problems.add(slug + ": compileOnly (" + compileOnly.get(slug) + ") and localRuntime (" + version + ") differ");
            }
            List<VersionRange> ranges = rangesOf(mod.getKey());
            Optional<ThirdPartyVersion> parsed = modVersion(version);
            if (parsed.isEmpty() || ranges.stream().noneMatch(range -> range.contains(parsed.get()))) {
                problems.add(slug + " " + version + " is outside the verified versions " + ranges);
            }
        }
        return problems;
    }

    private static List<VersionRange> rangesOf(String modId) {
        for (ThirdPartyCompat row : ThirdPartyTable.ALL) {
            if (row.modId().equals(modId)) {
                return row.supported();
            }
            for (Requirement required : row.also()) {
                if (required.modId().equals(modId)) {
                    return required.supported();
                }
            }
        }
        throw new IllegalStateException(modId + " is not in the table");
    }

    @Test
    void theDevelopmentDependenciesAreInsideTheVerifiedVersionsAndTheCompileOnesMatchThem() throws IOException {
        assertEquals(List.of(), checkGradle(read("build.gradle")));
    }

    @Test
    void theGradleCheckFailsWhenAVersionLeavesTheRangeOrTheLinesDiffer() throws IOException {
        String gradle = read("build.gradle");
        assertEquals(List.of(), checkGradle(gradle));
        // the development run moves to a version nobody has tried
        assertTrue(checkGradle(gradle.replace("localRuntime \"maven.modrinth:integrated-terminals:1.21.1-1.11.2\"",
                "localRuntime \"maven.modrinth:integrated-terminals:1.21.1-1.12.0\"")).size() >= 2);
        // compileOnly without transitive = false
        assertEquals(1, checkGradle(gradle.replace("compileOnly(\"maven.modrinth:common-capabilities:1.21.1-2.11.6\") {\n        transitive = false\n    }",
                "compileOnly \"maven.modrinth:common-capabilities:1.21.1-2.11.6\"")).size());
        // a compileOnly line is gone
        assertTrue(checkGradle(gradle.replace("compileOnly(\"maven.modrinth:cyclops-core:mbABgx56\")", "// gone(\"maven.modrinth:cyclops-core:mbABgx56\")"))
                .stream().anyMatch(p -> p.contains("cyclops-core")));
    }
}
