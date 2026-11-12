package pnnh.mcfloatingbackpack.tank;

/**
 * 048: the numbers behind the level bar drawn on a tank in the panel's bottom area: a thin vertical bar at the cell's left edge. Pure
 * logic so the pixels can be tested.
 */
public final class TankBar {
    /** Width and height of the bar, in pixels; it sits at (1, 1) inside the 18 pixel cell. */
    public static final int WIDTH = 2;
    public static final int HEIGHT = 14;
    public static final int OFFSET = 1;
    /** The dark backing under the bar. */
    public static final int COLOR_BACKING = 0xFF1A1A1A;

    private TankBar() {
    }

    /** How many of {@code heightPx} pixels are filled: none when empty (or when there is no capacity), at least one when not, all when full or over. */
    public static int fillPixels(int amountMb, int capacityMb, int heightPx) {
        if (amountMb <= 0 || capacityMb <= 0 || heightPx <= 0) {
            return 0;
        }
        double ratio = Math.min(1.0, (double) amountMb / capacityMb);
        return Math.max(1, (int) Math.round(heightPx * ratio));
    }
}
