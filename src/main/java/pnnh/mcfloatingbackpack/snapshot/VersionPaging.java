package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 057: one page of a backpack's versions, as the snapshot window asks for it: limited to one kind (or all), a page of at most so many
 * rows; a page that is gone (the list got shorter) is the last one. Pure logic.
 */
public final class VersionPaging {
    /** @param total how many there are of the chosen kind, in all pages */
    public record Page<T>(List<T> items, int page, int pages, int total) {
    }

    private VersionPaging() {
    }

    /**
     * @param newestFirst every version of the backpack
     * @param kind 0 for all, else the byte of the kind to keep
     * @param page from 1
     * @param pageSize at least 1
     */
    public static <T> Page<T> page(List<T> newestFirst, byte kind, Function<T, SnapshotKind> kindOf, int page, int pageSize) {
        List<T> chosen = new ArrayList<>();
        for (T version : newestFirst) {
            SnapshotKind of = kindOf.apply(version);
            if (kind == 0 || (of != null && of.code() == kind)) {
                chosen.add(version);
            }
        }
        int size = Math.max(1, pageSize);
        int pages = Math.max(1, (chosen.size() + size - 1) / size);
        int shown = Math.max(1, Math.min(page, pages));
        int from = (shown - 1) * size;
        int to = Math.min(chosen.size(), from + size);
        return new Page<>(List.copyOf(chosen.subList(Math.min(from, chosen.size()), to)), shown, pages, chosen.size());
    }

    /** The kind button of the window: all, timed, change, before an update, and round again. */
    public static byte nextKind(byte kind) {
        return switch (kind) {
            case 0 -> SnapshotKind.TIMED.code();
            case 1 -> SnapshotKind.CHANGE.code();
            case 2 -> SnapshotKind.UPGRADE.code();
            default -> 0;
        };
    }
}
