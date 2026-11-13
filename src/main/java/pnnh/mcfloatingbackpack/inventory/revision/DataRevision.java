package pnnh.mcfloatingbackpack.inventory.revision;

/**
 * 040: the revision of the data a backpack stores, which each save stamps on the backpack ({@code data_revision}). A backpack stamped
 * with a revision higher than {@link #CURRENT} was saved by a newer version of the mod, which may have stored what this version
 * cannot round-trip (a field it does not know is dropped the next time the backpack is saved); such a backpack is protected.
 *
 * <p>{@link #CURRENT} goes up only when the stored data changes in a way an older version cannot keep: a new stored field, a field that
 * means something else now, a new kind of item that only lives in a backpack. A change of a rule or of the interface, or a bug fix,
 * does not raise it. See {@code docs/plan/release-process.md}. 1 is the first version that stamps (040); 2 added the liquids a backpack holds (048); 3 added nine categories of extension rows and their items (051); 4 added the mobile jukebox, an item that only lives in a backpack (053); 5 added the mobile crafting table, another (054); a backpack without a stamp reads as
 * 0. Pure logic with no Minecraft types.
 */
public final class DataRevision {
    /** The revision this version stamps and understands. */
    public static final int CURRENT = 5;

    private DataRevision() {
    }

    /** What a stored value means: missing or negative reads as 0 (no stamp). */
    public static int readStored(Integer stored) {
        return stored == null || stored < 0 ? 0 : stored;
    }

    /** Whether a backpack stamped with {@code stored} was saved by a newer version than this one. */
    public static boolean isNewer(int stored) {
        return stored > CURRENT;
    }
}
