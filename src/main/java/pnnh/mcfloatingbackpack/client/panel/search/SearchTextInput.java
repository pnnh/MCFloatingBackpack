package pnnh.mcfloatingbackpack.client.panel.search;

/**
 * 042: the search box's text and caret. Unlike the tag box (018) any character is taken (Chinese included), moves and deletions go
 * by code point so a surrogate pair is never split, and the text stops at {@link SearchQuery#MAX_CODEPOINTS} code points. The only
 * selection there is is "all": after {@link #selectAll()} the next insertion, backspace or delete replaces the whole text first, and
 * any movement key drops the selection. No Minecraft types.
 */
public final class SearchTextInput {
    private final StringBuilder text = new StringBuilder();
    /** UTF-16 index of the caret. */
    private int caret;
    private boolean allSelected;

    public String text() {
        return text.toString();
    }

    public int caret() {
        return caret;
    }

    public boolean isEmpty() {
        return text.length() == 0;
    }

    public boolean hasSelection() {
        return allSelected && !text.isEmpty();
    }

    /** Types one code point; control characters and anything past the limit are refused. */
    public void insert(int codePoint) {
        if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) {
            return;
        }
        dropSelectedText();
        if (text.codePointCount(0, text.length()) >= SearchQuery.MAX_CODEPOINTS) {
            return;
        }
        String chars = new String(Character.toChars(codePoint));
        text.insert(caret, chars);
        caret += chars.length();
    }

    /** Pastes text: what is refused is dropped and what does not fit is cut off. */
    public void paste(String pasted) {
        if (pasted == null) {
            return;
        }
        pasted.codePoints().forEach(this::insert);
    }

    public void backspace() {
        if (replaceSelection()) {
            return;
        }
        if (caret > 0) {
            int from = text.offsetByCodePoints(caret, -1);
            text.delete(from, caret);
            caret = from;
        }
    }

    public void delete() {
        if (replaceSelection()) {
            return;
        }
        if (caret < text.length()) {
            int to = text.offsetByCodePoints(caret, 1);
            text.delete(caret, to);
        }
    }

    public void left() {
        allSelected = false;
        if (caret > 0) {
            caret = text.offsetByCodePoints(caret, -1);
        }
    }

    public void right() {
        allSelected = false;
        if (caret < text.length()) {
            caret = text.offsetByCodePoints(caret, 1);
        }
    }

    public void home() {
        allSelected = false;
        caret = 0;
    }

    public void end() {
        allSelected = false;
        caret = text.length();
    }

    /** Puts the caret at a UTF-16 index (clamped to the text), e.g. from a mouse click. */
    public void setCaret(int index) {
        allSelected = false;
        int clamped = Math.max(0, Math.min(index, text.length()));
        // never inside a surrogate pair
        if (clamped > 0 && clamped < text.length() && Character.isLowSurrogate(text.charAt(clamped))) {
            clamped--;
        }
        caret = clamped;
    }

    public void selectAll() {
        allSelected = !text.isEmpty();
        caret = text.length();
    }

    public void clear() {
        text.setLength(0);
        caret = 0;
        allSelected = false;
    }

    /** Backspace or delete with everything selected clears the text; true when that happened. */
    private boolean replaceSelection() {
        if (hasSelection()) {
            clear();
            return true;
        }
        allSelected = false;
        return false;
    }

    private void dropSelectedText() {
        if (hasSelection()) {
            clear();
        }
        allSelected = false;
    }
}
