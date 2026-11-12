package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 039: the language keys of the version warning, and the wiring that no pure test can see (the network and the login hook need
 * Minecraft): the two payloads are optional and registered apart, the protocol number is untouched, the hello is only sent to a
 * client that has the channel, and nothing was added that the feature must not add (a config, a command).
 */
class VersionCheckResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Set<String> keys(String json) {
        Set<String> out = new TreeSet<>();
        Matcher m = KEY.matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String value(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        assertTrue(m.find(), key);
        return m.group(1);
    }

    @Test
    void bothLanguagesHaveTheThreeWarningsWithBothVersionsAndTheBackupReminder() throws IOException {
        String zh = read(LANG.resolve("zh_cn.json"));
        String en = read(LANG.resolve("en_us.json"));
        assertEquals(keys(zh), keys(en), "the two language files must have the same keys");
        for (String name : new String[] {"older", "newer", "unknown"}) {
            String key = "message.mcfloatingbackpack.version." + name;
            for (String text : new String[] {value(zh, key), value(en, key)}) {
                assertTrue(text.contains("%1$s") && text.contains("%2$s"), key + ": both versions are named: " + text);
            }
            assertTrue(value(zh, key).contains("备份"), key);
            assertTrue(value(en, key).contains("back up"), key);
        }
        assertTrue(value(zh, "message.mcfloatingbackpack.version.older").contains("升级"));
        assertTrue(value(zh, "message.mcfloatingbackpack.version.newer").contains("降级"));
        assertTrue(value(en, "message.mcfloatingbackpack.version.older").contains("update"));
        assertTrue(value(en, "message.mcfloatingbackpack.version.newer").contains("downgrade"));
    }

    @Test
    void theTwoPayloadsAreOptionalAndRegisteredApartFromTheOthers() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        // the protocol number of the others is as it was
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"19\")"));
        // the version check has a registrar of its own, optional, with a version that never changes
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"1\").optional()"));
        int own = network.indexOf("PayloadRegistrar versionCheck");
        int others = network.indexOf("PayloadRegistrar registrar = ");
        assertTrue(own >= 0 && others > own);
        assertTrue(network.contains("versionCheck.playToClient(VersionHelloPayload.TYPE"));
        assertTrue(network.contains("versionCheck.playToServer(VersionReportPayload.TYPE"));
        // and not among the registrations of the others
        assertFalse(network.contains("registrar.playToClient(VersionHelloPayload"));
        assertFalse(network.contains("registrar.playToServer(VersionReportPayload"));
    }

    @Test
    void theHelloIsOnlySentToAClientThatHasTheChannel() throws IOException {
        String version = read(SRC.resolve("version/VersionNetwork.java"));
        int check = version.indexOf("hasChannel(VersionHelloPayload.TYPE)");
        int send = version.indexOf("PacketDistributor.sendToPlayer(player, new VersionHelloPayload");
        assertTrue(check >= 0 && send > check, "hasChannel must come before the hello is sent");
        // the owner of a single player game or a LAN world is not told
        assertTrue(version.contains("isSingleplayerOwner(player.getGameProfile())"));
        // nobody is refused or removed
        assertFalse(version.contains("disconnect("));
        assertFalse(version.contains(".kick"));
    }

    @Test
    void theLoginAndLogoutHooksAreWired() throws IOException {
        String main = read(SRC.resolve("MCFloatingBackpack.java"));
        assertTrue(main.contains("PlayerEvent.PlayerLoggedInEvent"));
        assertTrue(main.contains("VersionNetwork.onLogin(player)"));
        assertTrue(main.contains("PlayerVersions.forget(player.getUUID())"));
    }

    @Test
    void theDiagnosticsHeaderCarriesTheClientVersion() throws IOException {
        String watch = read(SRC.resolve("diag/BackpackWatchService.java"));
        assertTrue(watch.contains("Client mod version: {}"));
        assertTrue(watch.contains("PlayerVersions.describe(player.getUUID())"));
    }

    @Test
    void thePredictionIsGenerousForAnUnknownCategoryRow() throws IOException {
        String placement = read(SRC.resolve("client/panel/ClientPlacement.java"));
        assertTrue(placement.contains("isUnknownRow(slot)"));
        // the server's rule is the same as before: the matcher still refuses a category it does not know
        String matcher = read(SRC.resolve("inventory/extension/ItemCategoryMatcher.java"));
        assertTrue(matcher.contains("if (key == null) {\n            return false;"));
    }

    @Test
    void theFeatureAddsNoConfigAndNoCommand() throws IOException {
        String diagConfig = read(SRC.resolve("diag/DiagConfig.java"));
        String compatConfig = read(SRC.resolve("compat/CompatConfig.java"));
        assertFalse(diagConfig.toLowerCase().contains("version"));
        assertFalse(compatConfig.toLowerCase().contains("versioncheck"));
        String commands = read(SRC.resolve("snapshot/SnapshotCommands.java"));
        assertFalse(commands.contains("literal(\"version"));
    }

    @Test
    void theDebugSwitchesAreOffUnlessGiven() throws IOException {
        String debug = read(SRC.resolve("version/VersionDebug.java"));
        assertTrue(debug.contains("Boolean.getBoolean(PREFIX + \"checkOwner\")"));
        assertTrue(debug.contains("Boolean.getBoolean(PREFIX + \"legacyClient\")"));
        assertTrue(debug.contains("System.getProperty(PREFIX + \"serverVersion\")"));
        assertTrue(debug.contains("System.getProperty(PREFIX + \"clientVersion\")"));
        String gradle = read(Path.of("build.gradle"));
        assertTrue(gradle.contains("startsWith('mcfb.')"));
    }
}
