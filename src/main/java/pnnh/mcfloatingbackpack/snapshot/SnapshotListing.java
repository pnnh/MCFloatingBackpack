package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 030: which backpacks the snapshot list shows, in which order, on which page, and which backpacks' snapshots {@code trim} deletes.
 * A listing row is one backpack: its newest snapshot and how many snapshots it has. Pure logic: the preview and the real
 * {@code trim} use the same {@link #trimCandidates}, so they cannot disagree.
 */
public final class SnapshotListing {
    public static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** One backpack in a listing. */
    public record Group(SnapshotMeta newest, int versions) {
        public UUID uuid() {
            return newest.uuid();
        }
    }

    /** @param owner only this player's backpacks, or null for all; @param inactiveDays only backpacks whose newest snapshot is older than this many days, or -1 */
    public record Filter(UUID owner, long inactiveDays) {
        public static final Filter NONE = new Filter(null, -1);
    }

    /** @param inRange the page number exists (page 1 of an empty list does) */
    public record Page(List<Group> items, int page, int pages, int total, boolean inRange) {
    }

    private SnapshotListing() {
    }

    public static List<Group> select(Collection<Group> groups, Filter filter, long now) {
        List<Group> kept = new ArrayList<>();
        for (Group group : groups) {
            if (filter.owner() != null && !filter.owner().equals(group.newest().owner())) {
                continue;
            }
            if (filter.inactiveDays() >= 0 && !olderThanDays(group, now, filter.inactiveDays())) {
                continue;
            }
            kept.add(group);
        }
        return kept;
    }

    /** The most recently snapshotted first; backpacks of the same moment keep a fixed order (by internal id). */
    public static List<Group> newestFirst(Collection<Group> groups) {
        List<Group> sorted = new ArrayList<>(groups);
        sorted.sort(Comparator.comparingLong((Group g) -> g.newest().time()).reversed().thenComparing(Group::uuid));
        return sorted;
    }

    /** @param page counted from 1 */
    public static Page page(List<Group> sorted, int page, int size) {
        int total = sorted.size();
        int pages = Math.max(1, (total + size - 1) / size);
        if (page < 1 || page > pages) {
            return new Page(List.of(), page, pages, total, false);
        }
        int from = (page - 1) * size;
        return new Page(List.copyOf(sorted.subList(from, Math.min(total, from + size))), page, pages, total, true);
    }

    /** The backpacks whose snapshots {@code trim} deletes: newest snapshot older than {@code days} days; 0 means all of them. */
    public static List<Group> trimCandidates(Collection<Group> groups, long now, int days) {
        List<Group> chosen = new ArrayList<>();
        for (Group group : groups) {
            if (days == 0 || olderThanDays(group, now, days)) {
                chosen.add(group);
            }
        }
        return chosen;
    }

    private static boolean olderThanDays(Group group, long now, long days) {
        return now - group.newest().time() > days * DAY_MS;
    }
}
