package pnnh.mcfloatingbackpack.worldmarker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 041: the wiring no behavior test can see because it needs Minecraft: the listener is on the event that comes before the world is
 * loaded, only the check writes the marker, the comparison is against the data revision, and nothing the feature must not add was added.
 */
class WorldMarkerWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String src(String relative) throws IOException {
        return read(SRC.resolve(relative));
    }

    @Test
    void theListenerIsOnTheEventThatComesBeforeTheWorldIsLoaded() throws IOException {
        assertTrue(src("MCFloatingBackpack.java").contains("addListener(WorldMarkerEvents::onServerAboutToStart)"));
        assertTrue(src("worldmarker/WorldMarkerEvents.java").contains("ServerAboutToStartEvent event"));
        // Not ServerStartingEvent: that one comes after loadLevel(), when the world is already loaded.
        assertFalse(src("worldmarker/WorldMarkerEvents.java").contains("ServerStartingEvent"));
    }

    @Test
    void onlyTheCheckWritesTheMarker() throws IOException {
        try (Stream<Path> files = Files.walk(SRC)) {
            List<Path> writers = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return read(p).contains("WorldMarkerStore.write(");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).toList();
            assertEquals(List.of(SRC.resolve("worldmarker/WorldMarkerCheck.java")), writers);
        }
    }

    @Test
    void theRefusalIsAnExceptionThrownOutOfTheStart_andNothingElseInTheEventsClassWrites() throws IOException {
        String events = src("worldmarker/WorldMarkerEvents.java");
        assertTrue(events.contains("throw new ReportedException(CrashReport.forThrowable("));
        assertTrue(events.contains("if (result.refused())"));
        assertFalse(events.contains("WorldMarkerStore.write"));
        assertFalse(events.contains("Files."));
    }

    @Test
    void aSinglePlayerClientIsLetOutOfItsWaitBeforeTheRefusalIsThrown() throws IOException {
        // Without this the game hangs on the loading screen: the client waits for a listener that only loadLevel() makes (2026-11-11).
        String events = src("worldmarker/WorldMarkerEvents.java");
        int release = events.indexOf("releaseWaitingClient(server);");
        int thrown = events.indexOf("throw new ReportedException(");
        assertTrue(release > 0 && release < thrown, "the client is released before the exception is thrown");
        assertTrue(events.contains("server.isSingleplayer()"));
        assertTrue(events.contains("\"progressListenerFactory\""));
        assertTrue(events.contains(".create(0)"));
    }

    @Test
    void theComparisonIsWithTheDataRevision_notTheModVersion() throws IOException {
        String events = src("worldmarker/WorldMarkerEvents.java");
        assertTrue(events.contains("DataRevision.CURRENT"));
        assertTrue(events.contains("VersionDebug.acceptDowngrade()"));
        assertTrue(events.contains("VersionDebug.worldMarkerRevisionOverride()"));
        assertFalse(src("worldmarker/WorldMarkerGuard.java").contains("ModVersions"));
    }

    @Test
    void noPlayerDataIsTouched() throws IOException {
        try (Stream<Path> files = Files.walk(SRC.resolve("worldmarker"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = read(file);
                assertFalse(text.contains("PlayerList"), file + " must not touch the player list");
                assertFalse(text.contains("playerdata"), file + " must not touch player data");
                assertFalse(text.contains("BackpackGate"), file + " must leave 040's gate alone");
            }
        }
    }

    @Test
    void onlyTheEventsClassMeetsMinecraft() throws IOException {
        try (Stream<Path> files = Files.walk(SRC.resolve("worldmarker"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("WorldMarkerEvents.java")) {
                    continue;
                }
                String text = read(file);
                assertFalse(text.contains("import net.minecraft") || text.contains("import net.neoforged"), file + " must be free of Minecraft types");
            }
        }
    }

    @Test
    void theBuildForwardsAcceptDowngradeWithoutTheDebugPrefix() throws IOException {
        String build = read(Path.of("build.gradle"));
        assertTrue(build.contains("'acceptDowngrade'"));
        assertTrue(build.contains("'mcfloatingbackpack.acceptDowngrade'"));
    }

    @Test
    void noConfigCommandLanguageKeyOrNetworkPacketWasAdded() throws IOException {
        String network = src("network/BackpackNetwork.java");
        assertTrue(network.contains("versioned(\"18\")"));
        try (Stream<Path> files = Files.walk(SRC.resolve("worldmarker"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = read(file);
                assertFalse(text.contains("Commands.literal"), file + " must add no command");
                assertFalse(text.contains("Component.translatable"), file + " uses plain text, not language keys");
                assertFalse(text.contains("ModConfigSpec"), file + " must add no config");
                assertFalse(text.contains("CustomPacketPayload"), file + " must add no packet");
            }
        }
    }
}
