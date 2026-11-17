package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 057: which snapshots of one backpack go when it has more than it may keep of a kind: each kind has its own number
 * ({@link SnapshotKind#keep()}), and a kind's clean-up never touches another kind's snapshots. Replaces 030's single count of five.
 * Pure logic.
 */
public final class SnapshotRetention {
    /** One snapshot of a backpack, as far as retention is concerned. */
    public record Entry(long time, SnapshotKind kind) {
    }

    private SnapshotRetention() {
    }

    /**
     * @param entries every readable snapshot of the backpack, in any order
     * @param kind the kind that has just been written
     * @return the times, among the snapshots of {@code kind}, that are older than the newest {@code kind.keep()} of them
     */
    public static List<Long> toDelete(Collection<Entry> entries, SnapshotKind kind) {
        List<Long> times = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.kind() == kind) {
                times.add(entry.time());
            }
        }
        times.sort(Comparator.reverseOrder());
        if (times.size() <= kind.keep()) {
            return List.of();
        }
        return new ArrayList<>(times.subList(kind.keep(), times.size()));
    }
}
