package pnnh.mcfloatingbackpack.client.panel;

/**
 * 042: where everything in the floating panel's title bar goes, for a given panel width: the tier icon at the left (008, always), the
 * backpack's number after it (026), and at the right, from the right edge in: the close button (007, left out at the smallest size),
 * the snapshot button (031) and the magnifier that opens the search box. While the search box is open it takes everything between
 * the tier icon and the right-hand end (the close button when there is one): number, magnifier and snapshot button are not shown.
 *
 * <p>The close button is left out by the caller (it asks for the smallest size). When the bar is too narrow the rest give way in
 * this order: snapshot button, then the number's room; the magnifier is always there. Pure arithmetic with no Minecraft types; all x
 * values are absolute (the panel's left edge is {@code panelX}).
 *
 * @param hasClose whether the close button is shown
 * @param closeLeft its left edge (meaningless without it)
 * @param hasSnapshot whether the snapshot button is shown
 * @param snapshotLeft its left edge
 * @param searchLeft the magnifier's left edge (shown whenever the box is not open)
 * @param textRightLimit where the number must stop: the left edge of the leftmost button shown
 * @param boxLeft the search box's left edge while it is open
 * @param boxRight its right edge (exclusive)
 */
public record TitleBarLayout(
        boolean hasClose,
        int closeLeft,
        boolean hasSnapshot,
        int snapshotLeft,
        int searchLeft,
        int textRightLimit,
        int boxLeft,
        int boxRight) {
    public static final int BUTTON_SIZE = 10;
    public static final int RIGHT_MARGIN = 3;
    public static final int GAP = 2;
    /** The number needs at least this much room (four characters) or the snapshot button gives way. */
    public static final int MIN_TEXT = 24;

    public static TitleBarLayout of(int panelX, int panelWidth, boolean showClose) {
        int right = panelX + panelWidth - RIGHT_MARGIN;
        int closeLeft = right - BUTTON_SIZE;
        // where the buttons start from: the close button's left edge less a gap, or the right edge when there is none
        int edge = showClose ? closeLeft - GAP : right;
        int textStart = panelX + TitleIdLayout.ICON_OFFSET + TitleIdLayout.ICON_SIZE + TitleIdLayout.GAP;

        int snapshotLeft = edge - BUTTON_SIZE;
        int searchWithSnapshot = snapshotLeft - GAP - BUTTON_SIZE;
        boolean hasSnapshot = searchWithSnapshot - TitleIdLayout.MARGIN - textStart >= MIN_TEXT;
        int searchLeft = hasSnapshot ? searchWithSnapshot : snapshotLeft;
        // 3 columns still have 54 pixels for the number with everything shown, so even there nothing has to give way
        return new TitleBarLayout(showClose, closeLeft, hasSnapshot, snapshotLeft, searchLeft, searchLeft, textStart, edge);
    }
}
