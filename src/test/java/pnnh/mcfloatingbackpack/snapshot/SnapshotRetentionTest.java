package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.SnapshotRetention.Entry;

/** 057: each kind keeps its own number; a kind's clean-up does not touch the others. */
class SnapshotRetentionTest {
    private static List<Entry> of(SnapshotKind kind, int count, long firstTime) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new Entry(firstTime + i, kind));
        }
        return out;
    }

    @Test
    void nothingGoesWhileTheKindIsWithinItsNumber() {
        assertEquals(List.of(), SnapshotRetention.toDelete(of(SnapshotKind.TIMED, 60, 1000), SnapshotKind.TIMED));
        assertEquals(List.of(), SnapshotRetention.toDelete(of(SnapshotKind.CHANGE, 20, 1000), SnapshotKind.CHANGE));
        assertEquals(List.of(), SnapshotRetention.toDelete(of(SnapshotKind.UPGRADE, 5, 1000), SnapshotKind.UPGRADE));
        assertEquals(List.of(), SnapshotRetention.toDelete(List.of(), SnapshotKind.TIMED));
    }

    @Test
    void theOldestOfTheKindGoWhenItIsOver() {
        assertEquals(List.of(1000L), SnapshotRetention.toDelete(of(SnapshotKind.TIMED, 61, 1000), SnapshotKind.TIMED));
        assertEquals(List.of(1001L, 1000L), SnapshotRetention.toDelete(of(SnapshotKind.CHANGE, 22, 1000), SnapshotKind.CHANGE));
        assertEquals(List.of(1000L), SnapshotRetention.toDelete(of(SnapshotKind.UPGRADE, 6, 1000), SnapshotKind.UPGRADE));
    }

    @Test
    void theOtherKindsAreNeverTouchedAndTheOrderOfTheInputDoesNotMatter() {
        List<Entry> all = new ArrayList<>();
        all.addAll(of(SnapshotKind.TIMED, 70, 5000));
        all.addAll(of(SnapshotKind.CHANGE, 30, 1000));
        all.addAll(of(SnapshotKind.UPGRADE, 8, 100));
        java.util.Collections.shuffle(all, new java.util.Random(7));
        List<Long> timed = SnapshotRetention.toDelete(all, SnapshotKind.TIMED);
        assertEquals(10, timed.size());
        assertEquals(List.of(5009L, 5008L, 5007L, 5006L, 5005L, 5004L, 5003L, 5002L, 5001L, 5000L), timed);
        assertEquals(10, SnapshotRetention.toDelete(all, SnapshotKind.CHANGE).size());
        assertEquals(List.of(102L, 101L, 100L), SnapshotRetention.toDelete(all, SnapshotKind.UPGRADE));
    }

    @Test
    void aTimedSnapshotNewerThanAChangeOneDoesNotSaveTheChangeOne() {
        List<Entry> all = new ArrayList<>(of(SnapshotKind.CHANGE, 21, 1000));
        all.addAll(of(SnapshotKind.TIMED, 3, 9000));
        assertEquals(List.of(1000L), SnapshotRetention.toDelete(all, SnapshotKind.CHANGE));
    }
}
