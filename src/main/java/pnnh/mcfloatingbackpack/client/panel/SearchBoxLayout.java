package pnnh.mcfloatingbackpack.client.panel;

/**
 * 042: the inside of the open search box: the typed text from the left, the number of hits right-aligned before the clear button,
 * and the clear button ({@code ×}) at the right end. The number is left out when the text would get less than {@link #MIN_TEXT}
 * pixels; the text area and the clear button never give way. Pure arithmetic with no Minecraft types.
 *
 * @param textLeft where the text starts
 * @param textRight where the text must stop (exclusive)
 * @param hasCount whether the hit count is shown
 * @param countLeft the count's left edge (meaningless without it)
 * @param clearLeft the clear button's left edge ({@link #CLEAR_SIZE} wide)
 */
public record SearchBoxLayout(int textLeft, int textRight, boolean hasCount, int countLeft, int clearLeft) {
    public static final int PAD_LEFT = 3;
    public static final int CLEAR_SIZE = 10;
    /** Space between the box's right edge and the clear button, and between the pieces. */
    public static final int GAP = 2;
    public static final int RIGHT_INSET = 1;
    /** The text area is never narrower than this because of the count. */
    public static final int MIN_TEXT = 36;

    public static SearchBoxLayout of(int boxLeft, int boxRight, int countTextWidth) {
        int clearLeft = boxRight - RIGHT_INSET - CLEAR_SIZE;
        int textLeft = boxLeft + PAD_LEFT;
        int beforeClear = clearLeft - GAP;
        int countLeft = beforeClear - countTextWidth;
        boolean hasCount = countTextWidth > 0 && countLeft - GAP - textLeft >= MIN_TEXT;
        int textRight = hasCount ? countLeft - GAP : beforeClear;
        return new SearchBoxLayout(textLeft, Math.max(textLeft, textRight), hasCount, countLeft, clearLeft);
    }
}
