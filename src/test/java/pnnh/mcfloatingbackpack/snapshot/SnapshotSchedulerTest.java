package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.SnapshotScheduler.Due;

/** 030: when a backpack's change is due for a snapshot. */
class SnapshotSchedulerTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void nothingIsDueBeforeTheQuietTime() {
        SnapshotScheduler<String> scheduler = new SnapshotScheduler<>();
        scheduler.mark(A, "v1", 0);
        assertTrue(scheduler.due(SnapshotScheduler.QUIET_MS - 1).isEmpty());
        assertFalse(scheduler.isEmpty());
    }

    @Test
    void aQuietBackpackIsDueAndTakenOut() {
        SnapshotScheduler<String> scheduler = new SnapshotScheduler<>();
        scheduler.mark(A, "v1", 0);
        List<Due<String>> due = scheduler.due(SnapshotScheduler.QUIET_MS);
        assertEquals(1, due.size());
        assertEquals(A, due.get(0).uuid());
        assertEquals("v1", due.get(0).value());
        assertTrue(scheduler.isEmpty());
        assertTrue(scheduler.due(SnapshotScheduler.QUIET_MS * 5).isEmpty());
    }

    @Test
    void anotherChangeRestartsTheQuietTimeAndKeepsTheLatestValue() {
        SnapshotScheduler<String> scheduler = new SnapshotScheduler<>();
        scheduler.mark(A, "v1", 0);
        scheduler.mark(A, "v2", 8_000);
        assertEquals(1, scheduler.size());
        assertTrue(scheduler.due(12_000).isEmpty());
        List<Due<String>> due = scheduler.due(18_000);
        assertEquals("v2", due.get(0).value());
    }

    @Test
    void aBackpackChangedAllTheTimeIsStillDueAfterTheMaximum() {
        SnapshotScheduler<Integer> scheduler = new SnapshotScheduler<>();
        long now = 0;
        scheduler.mark(A, 0, now);
        int last = 0;
        while (now + 5_000 < SnapshotScheduler.MAX_MS) {
            now += 5_000;
            last++;
            scheduler.mark(A, last, now);
            assertTrue(scheduler.due(now).isEmpty(), "not yet at " + now);
        }
        now = SnapshotScheduler.MAX_MS;
        List<Due<Integer>> due = scheduler.due(now);
        assertEquals(1, due.size());
        assertEquals(last, due.get(0).value());
    }

    @Test
    void drainAllTakesOutEverythingDueOrNot() {
        SnapshotScheduler<String> scheduler = new SnapshotScheduler<>();
        scheduler.mark(A, "a", 0);
        scheduler.mark(B, "b", 1);
        assertEquals(2, scheduler.drainAll().size());
        assertTrue(scheduler.isEmpty());
    }

    @Test
    void drainWhereTakesOutOnlyWhatIsAccepted() {
        SnapshotScheduler<String> scheduler = new SnapshotScheduler<>();
        scheduler.mark(A, "alice", 0);
        scheduler.mark(B, "bob", 0);
        List<Due<String>> taken = scheduler.drainWhere("alice"::equals);
        assertEquals(1, taken.size());
        assertEquals(A, taken.get(0).uuid());
        assertEquals(1, scheduler.size());
    }
}
