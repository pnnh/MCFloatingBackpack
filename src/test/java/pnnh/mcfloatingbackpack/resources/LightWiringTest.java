package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 052: what no pure test can see because the classes need Minecraft or another mod: that the light is LambDynamicLights' (as with
 * Traveler's Backpack) and the floating backpack lights nothing itself, that the dependency is optional and compile-time only, where
 * the backpack is read, the two places that ask the rule, and what was not added.
 */
class LightWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path LIGHT = SRC.resolve("light");
    private static final Path COMPAT = SRC.resolve("compat/lambdynlights");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theEntryPointIsDeclaredTheWayTravelersBackpackDoesIt() throws IOException {
        String toml = read(Path.of("src/main/templates/META-INF/neoforge.mods.toml"));
        assertTrue(toml.contains("[modproperties.\"${mod_id}\".\"yumi:entrypoints\"]"));
        assertTrue(toml.contains("\"lambdynlights:initializer\" = \"pnnh.mcfloatingbackpack.compat.lambdynlights.BackpackDynamicLight\""));
        int dependency = toml.indexOf("modId=\"lambdynlights\"");
        assertTrue(dependency > 0);
        String block = toml.substring(dependency, toml.indexOf("[[", dependency) > 0 ? toml.indexOf("[[", dependency) : toml.length());
        assertTrue(block.contains("type=\"optional\""), "never required: without it nothing shines");
        assertTrue(block.contains("side=\"CLIENT\""));
    }

    @Test
    void theDependencyIsCompileOnlyAndDevRuntimeOnly() throws IOException {
        String gradle = read(Path.of("build.gradle"));
        assertTrue(Pattern.compile("compileOnly\\(\"dev\\.lambdaurora\\.lambdynamiclights:lambdynamiclights-api:[^\"]+:mojmap\"").matcher(gradle).find());
        assertTrue(gradle.contains("localRuntime \"maven.modrinth:lambdynamiclights:"));
        assertFalse(gradle.contains("implementation \"dev.lambdaurora"));
        assertFalse(gradle.contains("jarJar"), "not bundled into the published jar");
    }

    @Test
    void onlyTheCompatPackageKnowsLambDynamicLightsAndTheLightPackageKnowsNoOtherMod() throws IOException {
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (file.startsWith(COMPAT)) {
                    continue;
                }
                assertFalse(read(file).contains("dev.lambdaurora"), file.toString());
            }
        }
        try (Stream<Path> files = Files.walk(LIGHT)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = read(file);
                assertFalse(text.contains("lambdynlights") || text.contains("lambdaurora"), file.toString());
                assertFalse(text.contains("setBlock"), "the floating backpack puts no block in the world: " + file);
            }
        }
    }

    @Test
    void theLuminanceIsForPlayersOnlyAndHonoursTheCompatLevel() throws IOException {
        String luminance = read(COMPAT.resolve("BackpackEntityLuminance.java"));
        assertTrue(luminance.contains("entity instanceof Player player"));
        assertTrue(luminance.contains("CompatConfig.policy().specificAdaptation(BackpackDynamicLight.MOD_ID)"));
        assertTrue(luminance.contains("PlayerLights.brightnessOf(player)"));
        String initializer = read(COMPAT.resolve("BackpackDynamicLight.java"));
        assertTrue(initializer.contains("implements DynamicLightsInitializer"));
        assertTrue(initializer.contains("registerContext.register(EntityType.PLAYER"));
        assertTrue(initializer.contains("MOD_ID = \"lambdynlights\""));
    }

    @Test
    void theBackpackIsOnlyReadAndOnlyTheBottomAreaCounts() throws IOException {
        String lights = read(LIGHT.resolve("PlayerLights.java"));
        assertTrue(lights.contains("BackpackRef.resolve(player)"), "the backpack the B key opens");
        assertTrue(lights.contains("contents.tools().bottom()"));
        assertFalse(lights.contains("BackpackGate") || lights.contains("saveInventory") || lights.contains("setStack"));
    }

    @Test
    void theTwoPlacesThatPutItemsIntoAToolAreaAskTheRule() throws IOException {
        for (String file : new String[] {"network/BackpackClickPayload.java", "client/panel/FloatingPanelInput.java"}) {
            String source = read(SRC.resolve(file));
            assertTrue(source.contains("ToolLightRules.check("), file);
            assertTrue(source.contains("message.mcfloatingbackpack.light.duplicate"), file);
        }
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        assertTrue(input.contains("isLoaded(\"lambdynlights\")"), "the player is told once that the light needs the mod");
        assertTrue(input.contains("message.mcfloatingbackpack.light.needs_mod"));
    }

    @Test
    void nothingWasAddedToTheNetworkTheDataOrTheConfigAndThereIsNoMixin() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("versioned(\"20\")"), "the protocol is 20 since 054");
        assertFalse(network.contains("Light"), "no packet for the light");
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 5;"), "the data revision is 5 since 054");
        assertEquals(1, Pattern.compile("defineListAllowEmpty").matcher(read(SRC.resolve("compat/CompatConfig.java"))).results().count(),
                "no config entry was added");
        assertFalse(Files.exists(Path.of("src/main/resources/mcfloatingbackpack.mixins.json")));
        assertFalse(read(Path.of("src/main/templates/META-INF/neoforge.mods.toml")).contains("[[mixins]]"));
        try (Stream<Path> files = Files.walk(LIGHT)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                assertFalse(read(file).contains("CustomPacketPayload"), file.toString());
            }
        }
    }

    @Test
    void thePureClassesHaveNoMinecraftTypes() throws IOException {
        for (String name : new String[] {"ToolLights", "ToolLightRules"}) {
            String source = read(LIGHT.resolve(name + ".java"));
            assertFalse(source.contains("net.minecraft"), name);
            assertFalse(source.contains("net.neoforged"), name);
        }
    }
}
