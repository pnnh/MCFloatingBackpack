package pnnh.mcfloatingbackpack.worldmarker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pnnh.mcfloatingbackpack.worldmarker.WorldMarkerCheck.Result;
import pnnh.mcfloatingbackpack.worldmarker.WorldMarkerCheck.Severity;
import pnnh.mcfloatingbackpack.worldmarker.WorldMarkerGuard.Decision;

/**
 * 041: the whole start-up check in a real directory. What matters most: a refusal and an accepted risk write nothing, a marker only goes
 * up, and a marker that cannot be read or written never keeps the player out.
 */
class WorldMarkerCheckTest {
    private static final int CURRENT = 1;
    private static final String VERSION = "0.1.0";

    @TempDir
    Path world;

    private Path file() {
        return world.resolve(WorldMarkerStore.RELATIVE_PATH);
    }

    private void put(String text) throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), text, StandardCharsets.UTF_8);
    }

    private String text() throws IOException {
        return Files.readString(file(), StandardCharsets.UTF_8);
    }

    private Result run(boolean accept, Integer override) {
        return WorldMarkerCheck.run(file(), CURRENT, accept, override, VERSION);
    }

    @Test
    void aWorldWithoutAMarkerIsStartedAndGetsOne() throws IOException {
        Result result = run(false, null);
        assertEquals(Decision.PASS_AND_WRITE, result.decision());
        assertEquals(new MarkerRead.Valid(new WorldMarker(CURRENT, VERSION)), WorldMarkerStore.read(file()));
        assertEquals(Severity.INFO, result.lines().get(0).severity());
        assertTrue(result.lines().get(0).text().contains("none"));
        assertFalse(result.refused());
        assertNull(result.crashTitle());
    }

    @Test
    void aLowerMarkerIsRaisedToTheCurrentRevision() throws IOException {
        put(new WorldMarker(0, "0.0.1").toText());
        run(false, null);
        assertEquals(new MarkerRead.Valid(new WorldMarker(CURRENT, VERSION)), WorldMarkerStore.read(file()));
    }

    @Test
    void aMarkerThatIsAlreadyRightIsLeftAloneAndNothingIsLogged() throws IOException {
        String before = new WorldMarker(CURRENT, VERSION).toText();
        put(before);
        Result result = run(false, null);
        assertEquals(Decision.PASS, result.decision());
        assertTrue(result.lines().isEmpty());
        assertEquals(before, text());
    }

    @Test
    void aHigherMarkerIsRefusedAndNothingIsWritten() throws IOException {
        String before = new WorldMarker(99, "9.9.9").toText();
        put(before);
        Result result = run(false, null);
        assertTrue(result.refused());
        assertEquals(before, text());
        assertNotNull(result.crashTitle());
        assertTrue(result.crashTitle().contains("99 > 1"));
        assertEquals(1, result.lines().size());
        assertEquals(Severity.ERROR, result.lines().get(0).severity());
        assertEquals(result.crashDetail(), result.lines().get(0).text());
        String detail = result.crashDetail();
        assertTrue(detail.contains("9.9.9") && detail.contains(file().toAbsolutePath().toString()));
        assertTrue(detail.contains("-Dmcfloatingbackpack.acceptDowngrade=true"));
    }

    @Test
    void thePathInTheMessageHasNoDotSegments() throws IOException {
        put(new WorldMarker(99, null).toText());
        Path awkward = world.resolve(".").resolve(WorldMarkerStore.RELATIVE_PATH.replace("/", "/./"));
        Result result = WorldMarkerCheck.run(awkward, CURRENT, false, null, VERSION);
        assertTrue(result.refused());
        assertFalse(result.crashDetail().contains("/./"));
    }

    @Test
    void aRefusalLeavesNoOtherFileBehind() throws IOException {
        put(new WorldMarker(2, null).toText());
        run(false, null);
        try (var files = Files.walk(world)) {
            assertEquals(java.util.List.of(file().getParent(), file()),
                    files.filter(p -> !p.equals(world)).sorted().collect(Collectors.toList()));
        }
    }

    @Test
    void anAcceptedRiskStartsTheWorldAndWritesNothing() throws IOException {
        String before = new WorldMarker(99, "9.9.9").toText();
        put(before);
        Result result = run(true, null);
        assertEquals(Decision.ACCEPTED, result.decision());
        assertFalse(result.refused());
        assertEquals(before, text());
        assertEquals(1, result.lines().size());
        assertEquals(Severity.WARN, result.lines().get(0).severity());
        assertTrue(result.lines().get(0).text().contains("99") && result.lines().get(0).text().contains("accepted"));
    }

    @Test
    void anAcceptedRiskDoesNotLowerTheMarker_soTheNextStartIsRefusedAgain() throws IOException {
        put(new WorldMarker(5, null).toText());
        run(true, null);
        assertTrue(run(false, null).refused());
    }

    @Test
    void theParameterMeansNothingWhenTheMarkerIsNotHigher() throws IOException {
        put(new WorldMarker(0, null).toText());
        assertEquals(Decision.PASS_AND_WRITE, run(true, null).decision());
    }

    @Test
    void deletingTheMarkerOrLoweringItLetsTheWorldIn() throws IOException {
        put(new WorldMarker(99, null).toText());
        assertTrue(run(false, null).refused());
        Files.delete(file());
        assertFalse(run(false, null).refused());
        put(new WorldMarker(99, null).toText());
        put(new WorldMarker(1, "0.1.0").toText());
        assertFalse(run(false, null).refused());
    }

    @Test
    void aCorruptMarkerIsWarnedAboutRewrittenAndNeverRefuses() throws IOException {
        put("data_revision=lots");
        Result result = run(false, null);
        assertFalse(result.refused());
        assertEquals(Severity.WARN, result.lines().get(0).severity());
        assertTrue(result.lines().get(0).text().contains("revision 0"));
        assertEquals(new MarkerRead.Valid(new WorldMarker(CURRENT, VERSION)), WorldMarkerStore.read(file()));
    }

    @Test
    void theDevelopmentOverrideWritesTheHigherRevision_andTheNextStartWithoutItIsRefused() throws IOException {
        assertFalse(run(false, 99).refused());
        assertEquals(99, WorldMarkerStore.read(file()).revision());
        assertTrue(run(false, null).refused());
    }

    @Test
    void theDevelopmentOverrideRaisesAMarkerThatWasCurrent() throws IOException {
        run(false, null);
        run(false, 99);
        assertEquals(99, WorldMarkerStore.read(file()).revision());
    }

    @Test
    void theDevelopmentOverrideNeverLowersAMarker() throws IOException {
        run(false, 99);
        run(true, 2);
        assertEquals(99, WorldMarkerStore.read(file()).revision());
    }

    @Test
    void aMarkerThatCannotBeWrittenNeverKeepsThePlayerOut() throws IOException {
        Path directory = Files.createDirectories(file().getParent());
        assumeFalse(!directory.toFile().setWritable(false) || Files.isWritable(directory), "this account can write to a read-only directory");
        try {
            Result result = run(false, null);
            assertEquals(Decision.PASS_AND_WRITE, result.decision());
            assertFalse(result.refused());
            assertEquals(Severity.WARN, result.lines().get(0).severity());
            assertTrue(result.lines().get(0).text().contains("no effect"));
        } finally {
            directory.toFile().setWritable(true);
        }
    }

    @Test
    void theMarkerOfAnotherModVersionIsUpdatedWithoutAWarning() throws IOException {
        put(new WorldMarker(CURRENT, "0.0.9").toText());
        Result result = run(false, null);
        assertEquals(Decision.PASS_AND_WRITE, result.decision());
        assertEquals(new MarkerRead.Valid(new WorldMarker(CURRENT, VERSION)), WorldMarkerStore.read(file()));
        assertTrue(result.lines().stream().noneMatch(line -> line.severity() != Severity.INFO));
    }
}
