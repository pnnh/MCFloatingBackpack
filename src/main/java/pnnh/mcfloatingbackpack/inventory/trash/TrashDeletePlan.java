package pnnh.mcfloatingbackpack.inventory.trash;

/**
 * 022: how many groups one operation destroys for good and when the player has to confirm it. The client uses this to
 * decide whether to ask; the server uses the very same rule to refuse an unconfirmed request, so the two cannot drift.
 * Pure logic with no Minecraft types.
 */
public final class TrashDeletePlan {
    /** An operation that destroys more than this many groups needs the player's confirmation. */
    public static final int THRESHOLD = 9;

    private TrashDeletePlan() {
    }

    /**
     * Groups the trash can cannot keep after the operation: the old ones pushed out and any of the new ones that are
     * pushed out in turn (a dump of twenty groups into an empty trash can keeps nine and destroys eleven).
     */
    public static int destroyed(int existingGroups, int incomingGroups) {
        return Math.max(0, Math.max(0, existingGroups) + Math.max(0, incomingGroups) - TrashBin.MAX_GROUPS);
    }

    public static boolean needsConfirm(int destroyedGroups) {
        return destroyedGroups > THRESHOLD;
    }
}
