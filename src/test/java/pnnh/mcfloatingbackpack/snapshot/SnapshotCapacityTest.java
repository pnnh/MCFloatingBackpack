package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.SnapshotCapacity.File;
import pnnh.mcfloatingbackpack.snapshot.SnapshotCapacity.Result;

/** 057: the order in which the snapshots go when the folder is over its limit. */
class SnapshotCapacityTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static File file(UUID uuid, long time, SnapshotKind kind, long bytes) {
        return new File(uuid, time, kind, bytes);
    }

    @Test
    void nothingGoesWhileTheFolderIsWithinTheLimit() {
        Result result = SnapshotCapacity.victims(List.of(file(A, 1, SnapshotKind.TIMED, 100), file(A, 2, SnapshotKind.CHANGE, 100)), 200);
        assertEquals(List.of(), result.victims());
        assertFalse(result.stillOver());
    }

    @Test
    void theOldestTimedGoFirstOfAllTheBackpacksThenTheOldestChangeOnes() {
        List<File> files = List.of(
                file(A, 10, SnapshotKind.TIMED, 10), file(A, 30, SnapshotKind.TIMED, 10), file(A, 99, SnapshotKind.CHANGE, 10),
                file(B, 20, SnapshotKind.TIMED, 10), file(B, 5, SnapshotKind.CHANGE, 10), file(B, 98, SnapshotKind.CHANGE, 10));
        // 60 bytes, limit 20: 40 must go
        Result result = SnapshotCapacity.victims(files, 20);
        assertEquals(List.of(file(A, 10, SnapshotKind.TIMED, 10), file(B, 20, SnapshotKind.TIMED, 10), file(A, 30, SnapshotKind.TIMED, 10), file(B, 5, SnapshotKind.CHANGE, 10)), result.victims());
        assertFalse(result.stillOver());
    }

    @Test
    void theNewestOfEachBackpackAndTheUpgradeKindAreNeverTaken() {
        List<File> files = List.of(
                file(A, 1, SnapshotKind.UPGRADE, 100), file(A, 2, SnapshotKind.TIMED, 100), file(A, 3, SnapshotKind.CHANGE, 100),
                file(B, 4, SnapshotKind.TIMED, 100));
        Result result = SnapshotCapacity.victims(files, 0);
        assertEquals(List.of(file(A, 2, SnapshotKind.TIMED, 100)), result.victims(), "A's newest is the change one, B's only one is its newest");
        assertTrue(result.stillOver());
    }

    @Test
    void whenOnlyProtectedOnesAreLeftNothingGoesAndItIsStillOver() {
        Result result = SnapshotCapacity.victims(List.of(file(A, 1, SnapshotKind.UPGRADE, 500), file(A, 2, SnapshotKind.CHANGE, 500)), 10);
        assertEquals(List.of(), result.victims());
        assertTrue(result.stillOver());
    }

    @Test
    void itStopsAsSoonAsEnoughIsFreed() {
        List<File> files = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            files.add(file(A, i, SnapshotKind.TIMED, 10));
        }
        files.add(file(A, 100, SnapshotKind.CHANGE, 10));
        // 110 bytes, limit 95: 15 must go: two files
        assertEquals(2, SnapshotCapacity.victims(files, 95).victims().size());
    }
}
