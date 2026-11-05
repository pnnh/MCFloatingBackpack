package pnnh.mcfloatingbackpack.client.panel.tags;

import pnnh.mcfloatingbackpack.inventory.tags.TagRules;

/**
 * 018: the text box's buffer and caret. Only characters a tag may contain get in (nothing is converted: a capital
 * letter is refused, not lowered), and the text never grows past {@link TagRules#MAX_LENGTH}. No Minecraft types.
 */
public final class TagTextInput {
    private final StringBuilder text = new StringBuilder();
    private int caret;

    public String text() {
        return text.toString();
    }

    public int caret() {
        return caret;
    }

    /** Types one character; refused if the tag could not contain it or the text is full. */
    public void type(char c) {
        if (TagRules.acceptsChar(c) && text.length() < TagRules.MAX_LENGTH) {
            text.insert(caret, c);
            caret++;
        }
    }

    /** Pastes text: refused characters are dropped, and what does not fit is cut off. */
    public void paste(String pasted) {
        if (pasted == null) {
            return;
        }
        for (int i = 0; i < pasted.length(); i++) {
            type(pasted.charAt(i));
        }
    }

    public void backspace() {
        if (caret > 0) {
            text.deleteCharAt(caret - 1);
            caret--;
        }
    }

    public void delete() {
        if (caret < text.length()) {
            text.deleteCharAt(caret);
        }
    }

    public void left() {
        caret = Math.max(0, caret - 1);
    }

    public void right() {
        caret = Math.min(text.length(), caret + 1);
    }

    public void home() {
        caret = 0;
    }

    public void end() {
        caret = text.length();
    }

    public void clear() {
        text.setLength(0);
        caret = 0;
    }
}
