package pnnh.mcfloatingbackpack.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.rules.DependencyRules.SourceFile;

/**
 * 020 / constitution VII: the dependency rules, as failing tests. The first group feeds the checker small made-up
 * inputs (including a counter-example for every kind of violation, to prove the checker does fail); the second group
 * runs it on the real repository.
 */
class DependencyRulesTest {
    private static final String TOML_OK = """
            [[mods]]
            modId="${mod_id}"

            [[dependencies.${mod_id}]]
                modId="neoforge"
                type="required"
                versionRange="[21.1,)"

            [[dependencies.${mod_id}]]
                modId="minecraft"
                type="required"

            [[dependencies.${mod_id}]]
                modId="jei"
                type="optional"
            """;

    // --- mods.toml ---------------------------------------------------------------------------------------------

    @Test
    void tomlWithOnlyOptionalThirdPartyDependenciesPasses() {
        assertEquals(List.of(), DependencyRules.checkModsToml(TOML_OK));
    }

    @Test
    void aRequiredThirdPartyDependencyFailsAndNamesTheMod() {
        List<String> violations = DependencyRules.checkModsToml(TOML_OK.replace("modId=\"jei\"\n    type=\"optional\"", "modId=\"jei\"\n    type=\"required\""));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("jei"), violations.get(0));
    }

    @Test
    void aDependencyWithoutATypeFails() {
        String toml = TOML_OK + "\n[[dependencies.${mod_id}]]\n    modId=\"othermod\"\n";
        assertEquals(1, DependencyRules.checkModsToml(toml).size());
    }

    // --- imports -----------------------------------------------------------------------------------------------

    @Test
    void whitelistedImportsPassAnywhere() {
        SourceFile file = new SourceFile("src/main/java/pnnh/mcfloatingbackpack/client/panel/X.java", """
                package pnnh.mcfloatingbackpack.client.panel;
                import java.util.List;
                import net.minecraft.client.Minecraft;
                import net.neoforged.bus.api.IEventBus;
                import com.mojang.logging.LogUtils;
                import org.lwjgl.glfw.GLFW;
                import io.netty.buffer.ByteBuf;
                import org.slf4j.Logger;
                import pnnh.mcfloatingbackpack.MCFloatingBackpack;
                import static net.minecraft.util.Mth.clamp;
                """);
        assertEquals(List.of(), DependencyRules.checkImports(List.of(file)));
    }

    @Test
    void aThirdPartyImportInsideCompatModIdIsAllowed() {
        SourceFile file = new SourceFile("src/main/java/pnnh/mcfloatingbackpack/compat/jei/P.java", "import mezz.jei.api.IModPlugin;\n");
        assertEquals(List.of(), DependencyRules.checkImports(List.of(file)));
    }

    @Test
    void aThirdPartyImportOutsideCompatFailsAndNamesFileAndImport() {
        SourceFile file = new SourceFile("src/main/java/pnnh/mcfloatingbackpack/client/panel/Y.java", "import mezz.jei.api.IModPlugin;\n");
        List<String> violations = DependencyRules.checkImports(List.of(file));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("Y.java") && violations.get(0).contains("mezz.jei.api.IModPlugin"), violations.get(0));
    }

    @Test
    void aThirdPartyImportDirectlyInCompatRootFails() {
        // compat/ itself is the mod's own code; only compat/<modid>/ may touch another mod's types.
        SourceFile file = new SourceFile("src/main/java/pnnh/mcfloatingbackpack/compat/Z.java", "import net.p3pp3rf1y.sophisticatedcore.api.X;\n");
        assertEquals(1, DependencyRules.checkImports(List.of(file)).size());
    }

    @Test
    void aStaticImportOfAThirdPartyTypeIsCheckedToo() {
        SourceFile file = new SourceFile("src/main/java/pnnh/mcfloatingbackpack/item/W.java", "import static com.tiviacz.travelersbackpack.Util.x;\n");
        assertEquals(1, DependencyRules.checkImports(List.of(file)).size());
    }

    // --- build.gradle ------------------------------------------------------------------------------------------

    @Test
    void thirdPartyCoordinatesOnlyInCompileOnlyOrLocalRuntimePass() {
        String gradle = """
                repositories {
                    maven {
                        url = "https://cursemaven.com"
                        content { includeGroup "curse.maven" }
                    }
                }
                dependencies {
                    compileOnly "mezz.jei:jei-1.21.1-common-api:19.70.0.469"
                    if (compat) {
                        localRuntime "curse.maven:sophisticated-core-618298:9385313"
                        localRuntime "maven.modrinth:iris:1.8.12+1.21.1-neoforge"
                    }
                    testImplementation "org.junit.jupiter:junit-jupiter:5.10.0"
                }
                """;
        assertEquals(List.of(), DependencyRules.checkBuildGradle(gradle));
    }

    @Test
    void aThirdPartyCoordinateInAnyOtherConfigurationFails() {
        for (String config : new String[] {"implementation", "runtimeOnly", "api", "jarJar", "compileOnlyApi"}) {
            List<String> violations = DependencyRules.checkBuildGradle(config + " \"mezz.jei:jei-1.21.1-neoforge:19.70.0.469\"\n");
            assertEquals(1, violations.size(), config);
            assertTrue(violations.get(0).contains(config), violations.get(0));
        }
        assertEquals(1, DependencyRules.checkBuildGradle("implementation \"curse.maven:x-1:2\"\n").size());
        assertEquals(1, DependencyRules.checkBuildGradle("implementation \"maven.modrinth:x:2\"\n").size());
    }

    // --- the real repository -----------------------------------------------------------------------------------

    @Test
    void theRealModsTomlHasOnlyOptionalThirdPartyDependencies() throws IOException {
        String toml = Files.readString(Path.of("src/main/templates/META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        assertEquals(List.of(), DependencyRules.checkModsToml(toml));
    }

    @Test
    void theRealSourcesTouchThirdPartyTypesOnlyUnderCompatModId() throws IOException {
        List<SourceFile> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            for (Path path : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                files.add(new SourceFile(path.toString().replace('\\', '/'), Files.readString(path, StandardCharsets.UTF_8)));
            }
        }
        assertTrue(files.size() > 50, "the scan must actually find the sources");
        assertEquals(List.of(), DependencyRules.checkImports(files));
    }

    @Test
    void theRealBuildFileKeepsThirdPartyModsOutOfTheShippedDependencies() throws IOException {
        String gradle = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);
        assertEquals(List.of(), DependencyRules.checkBuildGradle(gradle));
    }
}
