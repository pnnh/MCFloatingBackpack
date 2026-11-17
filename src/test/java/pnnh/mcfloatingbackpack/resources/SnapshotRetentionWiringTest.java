package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 057: the pieces are wired together as the plan says. Source text checks (the classes need Minecraft). */
class SnapshotRetentionWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    // ---- three kinds, the timed round, the hashes

    @Test
    void aChangeFeedsTheDebounceAndTheTimedWindowAndEachRoundIsWrittenAsItsOwnKind() throws IOException {
        String service = read("snapshot/SnapshotService.java");
        assertTrue(service.contains("scheduler.mark(backpack, change, now);") && service.contains("timed.mark(backpack, change, now);"));
        assertTrue(service.contains("write(scheduler.due(now), SnapshotKind.CHANGE);"));
        assertTrue(service.contains("timed.due(now)") && service.contains("write(asDue, SnapshotKind.TIMED);"));
        assertTrue(service.contains("new TimedWindow<>(settings.timedIntervalMs())"));
        assertFalse(service.contains("SnapshotPolicy"), "the single count of five is gone");
    }

    @Test
    void theHashIsKeptPerBackpackAndKindAndForgottenForAllKinds() throws IOException {
        String service = read("snapshot/SnapshotService.java");
        assertTrue(service.contains("Map<HashKey, Integer> lastHash") && service.contains("record HashKey(UUID uuid, SnapshotKind kind)"));
        assertTrue(service.contains("version.kind() == meta.kind()"), "the disk is read for the newest one of the same kind");
        assertTrue(service.contains("for (SnapshotKind kind : SnapshotKind.values())"), "forget clears every kind");
    }

    @Test
    void aWriteCleansUpOnlyItsOwnKind() throws IOException {
        String store = read("snapshot/SnapshotStore.java");
        assertTrue(store.contains("pruneKind(meta.uuid(), dir, meta.kind());"));
        assertTrue(store.contains("SnapshotRetention.toDelete(entries, kind)"));
        assertTrue(store.contains("SnapshotFiles.fileName(time, meta.kind())"));
    }

    @Test
    void theTimedIntervalComesFromTheSettingsAndTheDebugSwitch() throws IOException {
        assertTrue(read("snapshot/SnapshotConfig.java").contains("VersionDebug.timedSnapshotSecondsOverride()"));
        assertTrue(read("version/VersionDebug.java").contains("mcfloatingbackpack.debug.") && read("version/VersionDebug.java").contains("timedSnapshotSeconds"));
    }

    // ---- a change of the mod's version: pinning

    @Test
    void theVersionIsCheckedWhenTheServiceStartsAndOnlyThen() throws IOException {
        String service = read("snapshot/SnapshotService.java");
        assertTrue(service.contains("current.checkVersion();"));
        assertTrue(service.contains("ModVersion.current()"));
        assertTrue(service.contains("settings.enabled() ? pinNewest(state.lastModVersion(), now) : 0"), "off: nothing is pinned");
        assertTrue(service.contains("new SnapshotState(now, state.changeId() + 1, state.lastModVersion(), pinned, List.of())"));
        assertTrue(read("MCFloatingBackpack.java").contains("(ServerStartingEvent event) -> SnapshotService.start(event.getServer())"));
    }

    @Test
    void pinningWritesTheNewFileChecksItAndOnlyThenDeletesTheOldOne() throws IOException {
        String store = read("snapshot/SnapshotStore.java");
        String pin = store.substring(store.indexOf("public boolean pin("), store.indexOf("public boolean updateTarget("));
        int write = pin.indexOf("writeAtomic(tag, target)");
        int check = pin.indexOf("contents.equals(check.get().get(\"contents\"))");
        int deleteOld = pin.indexOf("deleteQuietly(old)");
        assertTrue(write > 0 && check > write && deleteOld > check, "write, then compare, then delete the old file");
        assertTrue(pin.contains("deleteQuietly(target);"), "a copy that differs is removed and the original kept");
        assertFalse(pin.contains("BackpackContents"), "the contents are carried as they are, never decoded");
        assertFalse(pin.contains("CODEC"));
    }

    @Test
    void theStateFileIsWrittenToATemporaryFileAndRenamed() throws IOException {
        String store = read("snapshot/SnapshotStore.java");
        assertTrue(store.contains("STATE_FILE + TEMP_EXTENSION") && store.contains("Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE)"));
    }

    // ---- files this version does not know

    @Test
    void aFileOfANewerFormatIsNeitherListedNorDeleted() throws IOException {
        String store = read("snapshot/SnapshotStore.java");
        assertTrue(store.contains("static final int VERSION = 2;"));
        assertTrue(store.contains("if (version > VERSION)") && store.contains("futureLogged.add(file)"));
        assertTrue(store.contains("if (kind == null || !knownVersion(tag, file))"), "listing skips them");
        assertTrue(store.contains("for (SnapshotMeta meta : versions(uuid))"), "deleting all goes through the listing");
        assertTrue(store.contains("!knownVersion(tag.get(), file)"), "the capacity clean-up reads the header first");
    }

    @Test
    void anOldFileWithoutAKindIsTheOrdinaryKind() throws IOException {
        assertTrue(read("snapshot/SnapshotKind.java").contains("CHANGE((byte) 2, \"\", 20)"));
    }
}
