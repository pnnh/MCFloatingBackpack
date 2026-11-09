package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 030: how many snapshots of one backpack are kept, and which ones go when there are more. Pure logic. */
public final class SnapshotPolicy {
    /** The newest this many snapshots of a backpack are kept. */
    public static final int KEEP = 5;

    private SnapshotPolicy() {
    }

    /** @return the times, of the given snapshot times (in any order), that are older than the newest {@code keep} of them; those are the ones to delete */
    public static List<Long> toPrune(List<Long> times, int keep) {
        List<Long> sorted = new ArrayList<>(times);
        sorted.sort(Comparator.reverseOrder());
        if (sorted.size() <= keep) {
            return List.of();
        }
        return new ArrayList<>(sorted.subList(Math.max(0, keep), sorted.size()));
    }
}
