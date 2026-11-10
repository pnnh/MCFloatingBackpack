package pnnh.mcfloatingbackpack.client.panel;

/**
 * 037: where the small yellow dot that says "some items are hidden" sits: on the top right corner of the tier icon in the title bar
 * (026: the icon is {@link TitleIdLayout#ICON_SIZE} wide, {@link TitleIdLayout#ICON_OFFSET} from the panel's left edge, one pixel
 * below its top). The dot is drawn on the icon, so it takes no width of its own and the backpack's number, which starts after the
 * icon, is never covered. Pure arithmetic with no Minecraft types.
 */
public final class TitleDotLayout {
    /** The dot is this many pixels wide and tall (its four corner pixels are left out when it is drawn). */
    public static final int SIZE = 5;
    /** Its left edge, from the panel's left edge: the icon's right edge (4 + 16) less the dot's width and a pixel in. */
    public static final int X = TitleIdLayout.ICON_OFFSET + TitleIdLayout.ICON_SIZE - SIZE + 1;
    /** Its top edge, from the panel's top edge: the icon's top. */
    public static final int Y = 1;
    /** The hover area is this many pixels bigger on every side, so that the dot is easy to point at. */
    public static final int HOVER_MARGIN = 1;

    private TitleDotLayout() {
    }

    public static int left(int panelX) {
        return panelX + X;
    }

    public static int top(int panelY) {
        return panelY + Y;
    }

    /** Whether the point is over the dot (with its hover margin); the panel's top edge limits the margin upwards. */
    public static boolean hits(int panelX, int panelY, double mouseX, double mouseY) {
        int x1 = left(panelX) - HOVER_MARGIN;
        int x2 = left(panelX) + SIZE + HOVER_MARGIN;
        int y1 = Math.max(panelY, top(panelY) - HOVER_MARGIN);
        int y2 = top(panelY) + SIZE + HOVER_MARGIN;
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }
}
