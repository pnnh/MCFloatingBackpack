package pnnh.mcfloatingbackpack.worldmarker;

import java.util.Objects;

/**
 * 041: the decision made when a server starts a world, from what the world's marker says and what this version is. Pure logic with no
 * Minecraft types. The table is in {@code specs/041-world-downgrade-guard/data-model.md}.
 */
public final class WorldMarkerGuard {
    /** What to do with the start, and whether the marker is to be written. */
    public enum Decision {
        /** Start; the marker is already right. */
        PASS,
        /** Start, and write the marker (it is missing, unusable, lower than it should be, or names another version). */
        PASS_AND_WRITE,
        /** Do not start: the world was saved by a newer data revision. Nothing is written. */
        REFUSE,
        /** Start although the marker is higher, because the player accepted the risk. Nothing is written. */
        ACCEPTED
    }

    private WorldMarkerGuard() {
    }

    /**
     * @param read           what the marker file gave
     * @param current        the data revision of this version ({@code DataRevision.CURRENT}); the only thing a marker is compared with
     * @param accept         whether the player gave {@code -Dmcfloatingbackpack.acceptDowngrade=true}
     * @param currentModVersion this version's number, or null when it cannot be told
     * @param target         the revision that would be written ({@link #revisionToWrite})
     */
    public static Decision decide(MarkerRead read, int current, boolean accept, String currentModVersion, int target) {
        if (!(read instanceof MarkerRead.Valid valid)) {
            return Decision.PASS_AND_WRITE;
        }
        int stored = valid.marker().dataRevision();
        if (stored > current) {
            return accept ? Decision.ACCEPTED : Decision.REFUSE;
        }
        boolean raises = target > stored;
        boolean otherVersion = currentModVersion != null && !Objects.equals(valid.marker().modVersion(), currentModVersion);
        return raises || otherVersion ? Decision.PASS_AND_WRITE : Decision.PASS;
    }

    /**
     * The revision to write: this version's, or the development override ({@code -Pmcfb.worldMarkerRevision=N}) when there is one, and
     * never lower than the revision the marker already has (a marker only goes up).
     */
    public static int revisionToWrite(Integer override, int current, MarkerRead read) {
        int wanted = override != null && override >= 0 ? override : current;
        return Math.max(wanted, read.revision());
    }
}
