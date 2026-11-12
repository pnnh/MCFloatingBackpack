package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

/** 042: the visible window of the typed text and click-to-caret. Every code point is 6 wide here. */
class SearchTextViewTest {
    private static final ToIntFunction<String> SIX = s -> s.codePointCount(0, s.length()) * 6;

    @Test
    void textThatFitsStartsAtZero() {
        assertEquals(0, SearchTextView.windowStart("abcd", 4, 24, SIX));
        assertEquals(0, SearchTextView.windowStart("", 0, 24, SIX));
    }

    @Test
    void longTextShowsTheStretchEndingAtTheCaret() {
        // 10 characters, 24 wide = 4 characters
        assertEquals(6, SearchTextView.windowStart("abcdefghij", 10, 24, SIX));
        assertEquals(2, SearchTextView.windowStart("abcdefghij", 6, 24, SIX));
        assertEquals(0, SearchTextView.windowStart("abcdefghij", 3, 24, SIX));
    }

    @Test
    void aSupplementaryCharacterIsNotSplitByTheWindow() {
        String text = "a😀b😀c";
        int start = SearchTextView.windowStart(text, text.length(), 12, SIX);
        // two characters fit: "😀c" (and the start is not inside a pair)
        assertEquals(2, text.codePointCount(start, text.length()));
        assertEquals(false, Character.isLowSurrogate(text.charAt(start)));
    }

    @Test
    void clicksLandOnTheNearestCharacterEdge() {
        // "abcd" starting at 0, each 6 wide: edges at 0, 6, 12, 18, 24
        assertEquals(0, SearchTextView.caretAt("abcd", 0, 0, SIX));
        assertEquals(0, SearchTextView.caretAt("abcd", 0, 2, SIX));
        assertEquals(1, SearchTextView.caretAt("abcd", 0, 4, SIX));
        assertEquals(2, SearchTextView.caretAt("abcd", 0, 11, SIX));
        assertEquals(4, SearchTextView.caretAt("abcd", 0, 99, SIX));
    }

    @Test
    void clicksInAScrolledWindowCountFromItsStart() {
        assertEquals(3, SearchTextView.caretAt("abcdefghij", 2, 4, SIX));
        assertEquals(2, SearchTextView.caretAt("abcdefghij", 2, -3, SIX));
    }
}
