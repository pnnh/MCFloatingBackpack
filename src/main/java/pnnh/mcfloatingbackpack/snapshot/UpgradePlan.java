package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 057: what to do with one backpack's snapshots when the mod's version has changed: pin its newest snapshot as one from before the
 * update (what the old version wrote is kept as it was), or, if the newest already is one, only note the new version on it; no
 * snapshot, nothing to do. A backpack keeps {@link SnapshotKind#UPGRADE}'s {@code keep()} of them, so the oldest are dropped when a new
 * one comes. Pure logic.
 */
public final class UpgradePlan {
    public enum Action { PIN, UPDATE_TARGET, NONE }

    /** @param target the time of the snapshot to pin or to update (0 for {@link Action#NONE}); @param drop the times of upgrade snapshots that go */
    public record Plan(Action action, long target, List<Long> drop) {
    }

    private UpgradePlan() {
    }

    /** @param snapshots every readable snapshot of the backpack, in any order */
    public static Plan plan(List<SnapshotRetention.Entry> snapshots) {
        if (snapshots.isEmpty()) {
            return new Plan(Action.NONE, 0L, List.of());
        }
        SnapshotRetention.Entry newest = snapshots.stream().max(Comparator.comparingLong(SnapshotRetention.Entry::time)).orElseThrow();
        if (newest.kind() == SnapshotKind.UPGRADE) {
            return new Plan(Action.UPDATE_TARGET, newest.time(), List.of());
        }
        List<Long> upgrades = new ArrayList<>();
        for (SnapshotRetention.Entry entry : snapshots) {
            if (entry.kind() == SnapshotKind.UPGRADE) {
                upgrades.add(entry.time());
            }
        }
        upgrades.sort(Comparator.naturalOrder());
        int keepOfTheOld = SnapshotKind.UPGRADE.keep() - 1;
        List<Long> drop = upgrades.size() > keepOfTheOld ? new ArrayList<>(upgrades.subList(0, upgrades.size() - keepOfTheOld)) : List.of();
        return new Plan(Action.PIN, newest.time(), drop);
    }
}
