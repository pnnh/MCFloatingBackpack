package pnnh.mcfloatingbackpack.inventory;

/**
 * 057: how many stacks of a backpack could not be read (an item this version no longer has) and were left as empty places, counted
 * per thread: whoever wants to know counts on its own thread, after a {@link #reset}, and takes the number when it is done (a snapshot
 * being restored, a player's backpacks being decoded again at login); what other threads lose meanwhile, a chunk being loaded for
 * example, counts into their own numbers, which nobody reads. Pure logic.
 */
public final class LostCells {
    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    private LostCells() {
    }

    public static void add(int cells) {
        COUNT.get()[0] += Math.max(0, cells);
    }

    /** The number counted on this thread since the last {@link #take} or {@link #reset}; counting starts again from zero. */
    public static int take() {
        int[] count = COUNT.get();
        int taken = count[0];
        count[0] = 0;
        return taken;
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }
}
