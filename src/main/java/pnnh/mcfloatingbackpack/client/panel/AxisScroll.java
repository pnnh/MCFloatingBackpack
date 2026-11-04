package pnnh.mcfloatingbackpack.client.panel;

/**
 * Scrolling along one axis of one panel area (016): an offset into {@code total} cells of which
 * {@code visible} are shown. No Minecraft types, so it can be unit-tested.
 */
public final class AxisScroll {
    private AxisScroll() {
    }

    /** Keeps the offset in {@code [0, total - visible]}; content that fits never scrolls. */
    public static int clamp(int offset, int total, int visible) {
        int max = Math.max(0, total - visible);
        return Math.max(0, Math.min(offset, max));
    }

    public static int by(int offset, int delta, int total, int visible) {
        return clamp(offset + delta, total, visible);
    }

    /** More content before the window (up / left). */
    public static boolean canBack(int offset) {
        return offset > 0;
    }

    /** More content after the window (down / right). */
    public static boolean canForward(int offset, int total, int visible) {
        return offset < Math.max(0, total - visible);
    }
}
