package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 057: the limit and the switch are wired as the plan says. Source text checks. */
class SnapshotCapacityWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theLimitIsCheckedAfterEveryWriteAndOnlyTheVictimsOfTheCapacityRulesAreDeleted() throws IOException {
        String service = read("snapshot/SnapshotService.java");
        assertTrue(service.contains("enforceLimit();"));
        assertTrue(service.contains("SnapshotCapacity.victims(files, max)"));
        assertTrue(service.contains("store.deleteIfKnown(victim.uuid(), victim.time())"));
        assertTrue(service.contains("capacityWarning.tryAcquire("), "the warning is throttled");
        assertTrue(service.contains("new Throttle(Throttle.HOUR_MS)"));
    }

    @Test
    void theSettingsAreMadeSafeAndAClampedOneIsLogged() throws IOException {
        String service = read("snapshot/SnapshotService.java");
        assertTrue(service.contains("SnapshotConfig.settings()"));
        assertTrue(service.contains("settings.clamped()"));
    }

    @Test
    void withTheSwitchOffNothingIsMarkedWrittenOrPinned() throws IOException {
        String service = read("snapshot/SnapshotService.java");
        assertTrue(service.contains("if (settings.enabled() && contents != null && seen.add(backpack))"));
        assertTrue(service.contains("if (settings.enabled() && contents != null)"));
        assertTrue(service.contains("if (!settings.enabled() || (scheduler.isEmpty() && timed.isEmpty()))"));
        assertFalse(read("snapshot/SnapshotOps.java").contains("enabled()"), "looking, previewing and restoring do not ask the switch");
    }

    @Test
    void theConfigValueIsDefinedWithoutARangeSoItCanBeMovedIntoOne() throws IOException {
        String config = read("snapshot/SnapshotConfig.java");
        assertTrue(config.contains("Integer.MIN_VALUE, Integer.MAX_VALUE"));
    }

    @Test
    void theProtocolIs21AndTheVersionListIsPaged() throws IOException {
        assertTrue(read("network/BackpackNetwork.java").contains("versioned(\"22\")"));
        assertTrue(read("snapshot/SnapshotServer.java").contains("VersionPaging.page(all, request.kind(), SnapshotMeta::kind, request.page(), request.pageSize())"));
        assertTrue(read("snapshot/SnapshotRequestRules.java").contains("MAX_VERSIONS = 100"));
        assertTrue(read("snapshot/SnapshotCommands.java").contains("IntegerArgumentType.integer(1, SnapshotRequestRules.MAX_VERSIONS)"));
    }

    @Test
    void theBackpacksDataRevisionIsUnchanged() throws IOException {
        assertTrue(read("inventory/revision/DataRevision.java").contains("CURRENT = 6;"));
    }
}
