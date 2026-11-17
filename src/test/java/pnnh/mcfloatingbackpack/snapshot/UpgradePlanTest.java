package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.SnapshotRetention.Entry;
import pnnh.mcfloatingbackpack.snapshot.UpgradePlan.Action;
import pnnh.mcfloatingbackpack.snapshot.UpgradePlan.Plan;

/** 057: what a change of the mod's version does to one backpack's snapshots. */
class UpgradePlanTest {
    private static Entry e(long time, SnapshotKind kind) {
        return new Entry(time, kind);
    }

    @Test
    void aBackpackWithoutSnapshotsHasNothingToPin() {
        assertEquals(new Plan(Action.NONE, 0L, List.of()), UpgradePlan.plan(List.of()));
    }

    @Test
    void theNewestSnapshotIsPinnedWhateverItsKind() {
        assertEquals(new Plan(Action.PIN, 30L, List.of()), UpgradePlan.plan(List.of(e(10, SnapshotKind.CHANGE), e(30, SnapshotKind.TIMED), e(20, SnapshotKind.CHANGE))));
        assertEquals(new Plan(Action.PIN, 10L, List.of()), UpgradePlan.plan(List.of(e(10, SnapshotKind.CHANGE))));
    }

    @Test
    void ifTheNewestIsAlreadyBeforeTheUpdateOnlyItsNewVersionIsNoted() {
        assertEquals(new Plan(Action.UPDATE_TARGET, 50L, List.of()), UpgradePlan.plan(List.of(e(50, SnapshotKind.UPGRADE), e(40, SnapshotKind.UPGRADE), e(30, SnapshotKind.CHANGE))));
    }

    @Test
    void aSixthUpgradeSnapshotPushesOutTheOldest() {
        List<Entry> all = new ArrayList<>(List.of(e(1000, SnapshotKind.CHANGE)));
        for (int i = 1; i <= 5; i++) {
            all.add(e(i, SnapshotKind.UPGRADE));
        }
        assertEquals(new Plan(Action.PIN, 1000L, List.of(1L)), UpgradePlan.plan(all));
        all.remove(all.size() - 1);
        assertEquals(new Plan(Action.PIN, 1000L, List.of()), UpgradePlan.plan(all), "four old ones and the new one make five");
    }

    @Test
    void aBackpackWithOnlyUpgradeSnapshotsNotesTheNewestOne() {
        assertEquals(Action.UPDATE_TARGET, UpgradePlan.plan(List.of(e(1, SnapshotKind.UPGRADE), e(2, SnapshotKind.UPGRADE))).action());
    }
}
