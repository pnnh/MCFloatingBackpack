package pnnh.mcfloatingbackpack.client.panel;

/**
 * 031: the buttons at the right end of the floating panel's title bar: the close button (007) and, to its left, the snapshot
 * button. The backpack's number (026) is drawn from the tier icon up to {@link #textRightLimit}. The snapshot button is left out
 * when the bar is so narrow that the number would have less than {@value #MIN_TEXT} pixels. Pure arithmetic with no Minecraft
 * types.
 *
 * @param closeLeft the close button's left edge (the button is {@value #BUTTON_SIZE} wide, {@value #RIGHT_MARGIN} from the right edge)
 * @param hasSnapshot whether the snapshot button is shown
 * @param snapshotLeft its left edge (meaningless when it is not shown)
 * @param textRightLimit where the number must stop: the snapshot button's left edge or the close button's, less the gap
 */
public record TitleButtonsLayout(int closeLeft, boolean hasSnapshot, int snapshotLeft, int textRightLimit) {
    public static final int BUTTON_SIZE = 10;
    public static final int RIGHT_MARGIN = 3;
    public static final int GAP = 2;
    /** The number needs at least this much room (four characters) or the snapshot button gives way. */
    public static final int MIN_TEXT = 24;

    public static TitleButtonsLayout of(int panelX, int panelWidth) {
        int closeLeft = panelX + panelWidth - RIGHT_MARGIN - BUTTON_SIZE;
        int snapshotLeft = closeLeft - GAP - BUTTON_SIZE;
        int textStart = panelX + TitleIdLayout.ICON_OFFSET + TitleIdLayout.ICON_SIZE + TitleIdLayout.GAP;
        boolean has = snapshotLeft - TitleIdLayout.MARGIN - textStart >= MIN_TEXT;
        return new TitleButtonsLayout(closeLeft, has, snapshotLeft, has ? snapshotLeft : closeLeft);
    }
}
