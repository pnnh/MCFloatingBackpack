package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 057: which snapshots go when the snapshots of a world together take more than the limit: the oldest timed ones first, then the oldest
 * change ones. Never the newest snapshot of a backpack, never an upgrade one: if only those are left and the folder is still too big
 * it stays too big ({@link Result#stillOver()}). Pure logic.
 */
public final class SnapshotCapacity {
    /** One snapshot file as far as the clean-up is concerned. */
    public record File(UUID uuid, long time, SnapshotKind kind, long bytes) {
    }

    public record Result(List<File> victims, boolean stillOver) {
    }

    private SnapshotCapacity() {
    }

    public static Result victims(Collection<File> files, long maxBytes) {
        long total = 0;
        Map<UUID, Long> newest = new HashMap<>();
        for (File file : files) {
            total += file.bytes();
            newest.merge(file.uuid(), file.time(), Math::max);
        }
        long over = total - maxBytes;
        if (over <= 0) {
            return new Result(List.of(), false);
        }
        List<File> candidates = new ArrayList<>();
        for (File file : files) {
            if (!file.kind().isProtected() && file.time() != newest.get(file.uuid())) {
                candidates.add(file);
            }
        }
        candidates.sort(Comparator
                .comparingInt((File file) -> file.kind() == SnapshotKind.TIMED ? 0 : 1)
                .thenComparingLong(File::time)
                .thenComparing(File::uuid));
        List<File> victims = new ArrayList<>();
        long freed = 0;
        for (File file : candidates) {
            if (freed >= over) {
                break;
            }
            victims.add(file);
            freed += file.bytes();
        }
        return new Result(victims, freed < over);
    }
}
