package pnnh.mcfloatingbackpack.client.panel;

/**
 * 026: where the backpack's number is drawn in the panel's title bar: just right of the tier icon (008), and no further than
 * to the close button. Pure arithmetic with no Minecraft types: the text width comes from the caller.
 */
public record TitleIdLayout(int x, int maxWidth, boolean truncated) {
    /** The tier icon sits this far from the panel's left edge, is this wide, and the number starts this far after it. */
    public static final int ICON_OFFSET = 4;

    public static final int ICON_SIZE = 16;
    public static final int GAP = 3;
    /** Space kept free before the close button. */
    public static final int MARGIN = 2;

    /**
     * @param panelX the panel's left edge
     * @param closeLeft the left edge of the close button
     * @param textWidth how wide the whole number is when drawn
     */
    public static TitleIdLayout of(int panelX, int closeLeft, int textWidth) {
        int x = panelX + ICON_OFFSET + ICON_SIZE + GAP;
        int maxWidth = Math.max(0, closeLeft - x - MARGIN);
        return new TitleIdLayout(x, maxWidth, textWidth > maxWidth);
    }
}
