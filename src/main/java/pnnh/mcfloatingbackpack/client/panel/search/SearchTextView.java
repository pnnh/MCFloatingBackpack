package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.function.ToIntFunction;

/**
 * 042: which part of the typed text the box shows when it does not all fit, and which character a click lands on. The box shows
 * the stretch of text that ends at the caret and fits (so what is being typed is always in view); a click maps back to a caret
 * position in that stretch. The width of a piece of text comes from the caller (the font). No Minecraft types.
 */
public final class SearchTextView {
    private SearchTextView() {
    }

    /** The first UTF-16 index shown: the smallest start from which {@code text[start..caret]} still fits in {@code areaWidth}. */
    public static int windowStart(String text, int caret, int areaWidth, ToIntFunction<String> widthOf) {
        int caretAt = Math.max(0, Math.min(caret, text.length()));
        int start = 0;
        while (start < caretAt && widthOf.applyAsInt(text.substring(start, caretAt)) > areaWidth) {
            start += Character.charCount(text.codePointAt(start));
        }
        return start;
    }

    /** The caret position a click at {@code clickX} (relative to the text's left edge) means, in a window that starts at {@code start}. */
    public static int caretAt(String text, int start, int clickX, ToIntFunction<String> widthOf) {
        if (clickX <= 0) {
            return start;
        }
        int index = start;
        while (index < text.length()) {
            int next = index + Character.charCount(text.codePointAt(index));
            int before = widthOf.applyAsInt(text.substring(start, index));
            int after = widthOf.applyAsInt(text.substring(start, next));
            // the click is on this character: left half puts the caret before it, right half after it
            if (clickX < after) {
                return clickX - before < after - clickX ? index : next;
            }
            index = next;
        }
        return text.length();
    }
}
