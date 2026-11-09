package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.identity.BackpackIds;

/** 030: snapshot file names. */
class SnapshotFilesTest {
    @Test
    void aNameAndItsTimeRoundTrip() {
        assertEquals("1762700000123.dat", SnapshotFiles.fileName(1762700000123L));
        assertEquals(1762700000123L, SnapshotFiles.parseTime(SnapshotFiles.fileName(1762700000123L)));
        assertEquals(0L, SnapshotFiles.parseTime("0.dat"));
    }

    @Test
    void whatIsNotASnapshotNameIsRefused() {
        for (String bad : new String[] {null, "", ".dat", "abc.dat", "123.dat.tmp", "123.tmp", "12 3.dat", "-5.dat", "1234567890123456789.dat", "123"}) {
            assertEquals(-1L, SnapshotFiles.parseTime(bad), String.valueOf(bad));
        }
    }

    @Test
    void aTakenTimeMovesOnToTheNextFreeOne() {
        assertEquals(10L, SnapshotFiles.freeTime(10L, Set.of()));
        assertEquals(11L, SnapshotFiles.freeTime(10L, Set.of(10L)));
        assertEquals(13L, SnapshotFiles.freeTime(10L, Set.of(10L, 11L, 12L)));
        assertEquals(10L, SnapshotFiles.freeTime(10L, Set.of(11L)));
    }

    @Test
    void aFolderIsNamedByTheUuidAndTheIdThePlayerSees() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String name = SnapshotFiles.folderName(uuid);
        assertEquals("123e4567-e89b-12d3-a456-426614174000-" + BackpackIds.of(uuid), name);
        assertEquals(36 + 1 + BackpackIds.LENGTH, name.length());
        assertEquals(uuid, SnapshotFiles.parseFolder(name));
    }

    @Test
    void onlyTheUuidInAFolderNameCounts() {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals(uuid, SnapshotFiles.parseFolder("123e4567-e89b-12d3-a456-426614174000"), "the first version's folders");
        assertEquals(uuid, SnapshotFiles.parseFolder("123e4567-e89b-12d3-a456-426614174000-K7M2"));
        assertEquals(uuid, SnapshotFiles.parseFolder("123e4567-e89b-12d3-a456-426614174000-whatever-follows"));
        assertEquals(uuid, SnapshotFiles.parseFolder("123E4567-E89B-12D3-A456-426614174000-ZZZZ"));
    }

    @Test
    void aNameThatDoesNotStartWithAUuidIsNotOurs() {
        for (String bad : new String[] {null, "", "backup", "123e4567-e89b-12d3-a456-42661417400", "123e4567-e89b-12d3-a456-4266141740000", "123e4567-e89b-12d3-a456-426614174000K7M2", "x123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-42661417400g"}) {
            assertEquals(null, SnapshotFiles.parseFolder(bad), String.valueOf(bad));
        }
    }
}
