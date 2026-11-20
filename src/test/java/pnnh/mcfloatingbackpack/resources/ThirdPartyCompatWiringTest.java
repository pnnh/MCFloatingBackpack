package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** 058: the version check, the notices and the failure handling are wired as the plan says. Source text checks. */
class ThirdPartyCompatWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theStateIsWorkedOutWhenTheServerStartsAndClearedWhenItStops() throws IOException {
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("(ServerStartingEvent event) ->\n                pnnh.mcfloatingbackpack.compat.ThirdPartyCompatService.start(event.getServer())")
                || main.contains("ThirdPartyCompatService.start(event.getServer())"));
        assertTrue(main.contains("ThirdPartyCompatService.stop()"));
        assertTrue(main.contains("ThirdPartyNotices.onLogin(player)"));
    }

    @Test
    void theServiceUsesTheTableTheInstalledVersionsAndTheConfig() throws IOException {
        String service = read("compat/ThirdPartyCompatService.java");
        assertTrue(service.contains("CompatRuntimeState.evaluate(ThirdPartyTable.ALL, InstalledMods::versionOf, ThirdPartyConfig.allowUntested())"));
        assertTrue(service.contains("ThirdPartyTable.validate(ThirdPartyTable.ALL)"), "an unsound table switches everything off");
        assertTrue(service.contains("findingsOutOfRange()") && service.contains("LOGGER.warn("));
    }

    @Test
    void onlyOperatorsAreToldAndEveryTimeTheyLogIn() throws IOException {
        String notices = read("compat/ThirdPartyNotices.java");
        assertTrue(notices.contains("!player.hasPermissions(OP_LEVEL)"));
        assertTrue(notices.contains("static final int OP_LEVEL = 2;"));
        assertFalse(notices.contains("notified"), "no list of those told: it is a state, not a one-time notice");
        assertTrue(notices.contains("state.findingsOutOfRange()"));
    }

    @Test
    void aFailureIsToldToTheOperatorsWhoAreOnlineAndOnlyThey() throws IOException {
        String notices = read("compat/ThirdPartyNotices.java");
        assertTrue(notices.contains("server.getPlayerList().getPlayers()"));
        assertTrue(notices.contains("if (player.hasPermissions(OP_LEVEL))"));
        assertTrue(notices.contains("LOGGER.error(CompatLogText.failure(name), cause)"));
        assertTrue(read("compat/NetworkStorageBridges.java").contains("ThirdPartyNotices::failed"));
    }

    @Test
    void theDispatchCatchesLinkageErrorsAndRuntimeExceptionsOnly() throws IOException {
        String dispatch = read("compat/BridgeDispatch.java");
        assertTrue(dispatch.contains("catch (LinkageError | RuntimeException failure)"));
        assertTrue(dispatch.contains("if (current.fail(bridge.modId()))"), "reported once");
        assertFalse(dispatch.contains("catch (Throwable"));
        assertFalse(dispatch.contains("catch (Error"));
    }

    @Test
    void noReflectionIsUsedForTheThirdPartyCompatibility() throws IOException {
        try (Stream<Path> files = Files.walk(SRC.resolve("compat"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(text.contains("Class.forName"), file.toString());
                assertFalse(text.contains("java.lang.reflect"), file.toString());
                assertFalse(text.contains(".getDeclaredMethod("), file.toString());
            }
        }
    }

    @Test
    void theDebugSwitchOnlyChangesAModThatIsInstalled() throws IOException {
        String installed = read("compat/InstalledMods.java");
        assertTrue(installed.contains("if (real.isEmpty())") && installed.contains("VersionDebug.fakeModVersions()"));
    }
}
