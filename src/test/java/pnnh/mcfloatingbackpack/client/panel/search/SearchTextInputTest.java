package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 042: the search box's text model. */
class SearchTextInputTest {
    private static SearchTextInput typed(String s) {
        SearchTextInput input = new SearchTextInput();
        s.codePoints().forEach(input::insert);
        return input;
    }

    @Test
    void anyCharacterGoesIncludingChinese() {
        SearchTextInput input = typed("钻石 pick-1_A|\"@#/");
        assertEquals("钻石 pick-1_A|\"@#/", input.text());
        assertEquals(input.text().length(), input.caret());
    }

    @Test
    void controlCharactersAreRefused() {
        SearchTextInput input = typed("a\nb\tc\u0007");
        assertEquals("abc", input.text());
    }

    @Test
    void textStopsAtTheLimitInCodePoints() {
        SearchTextInput input = typed("x".repeat(150));
        assertEquals(SearchQuery.MAX_CODEPOINTS, input.text().length());
        input.insert('y');
        assertEquals(SearchQuery.MAX_CODEPOINTS, input.text().length());
    }

    @Test
    void aSupplementaryCharacterCountsOnceAndIsNeverSplit() {
        SearchTextInput input = typed("a😀b");
        assertEquals(4, input.text().length());
        input.left();
        input.left();
        assertEquals(1, input.caret());
        input.right();
        assertEquals(3, input.caret());
        input.backspace();
        assertEquals("ab", input.text());
        input.insert('c');
        assertEquals("acb", input.text());
    }

    @Test
    void pasteCutsWhatDoesNotFit() {
        SearchTextInput input = typed("x".repeat(98));
        input.paste("abcdef");
        assertEquals(SearchQuery.MAX_CODEPOINTS, input.text().codePointCount(0, input.text().length()));
        assertTrue(input.text().endsWith("ab"));
        input.clear();
        input.paste(null);
        input.paste("a\nb");
        assertEquals("ab", input.text());
    }

    @Test
    void movingAndEditingAtTheCaret() {
        SearchTextInput input = typed("abc");
        input.home();
        input.insert('x');
        assertEquals("xabc", input.text());
        input.end();
        input.backspace();
        assertEquals("xab", input.text());
        input.home();
        input.delete();
        assertEquals("ab", input.text());
        // at the start: backspace has nothing before it
        input.backspace();
        assertEquals("ab", input.text());
        input.end();
        // at the end: delete has nothing after it
        input.delete();
        assertEquals("ab", input.text());
    }

    @Test
    void selectAllThenTypingReplacesTheText() {
        SearchTextInput input = typed("old text");
        input.selectAll();
        assertTrue(input.hasSelection());
        input.insert('n');
        assertEquals("n", input.text());
        assertFalse(input.hasSelection());
    }

    @Test
    void selectAllThenBackspaceOrDeleteClears() {
        SearchTextInput input = typed("old");
        input.selectAll();
        input.backspace();
        assertEquals("", input.text());
        input = typed("old");
        input.selectAll();
        input.delete();
        assertEquals("", input.text());
    }

    @Test
    void aMovementKeyDropsTheSelection() {
        SearchTextInput input = typed("old");
        input.selectAll();
        input.left();
        assertFalse(input.hasSelection());
        input.insert('x');
        assertEquals("olxd", input.text());
    }

    @Test
    void selectAllOnNothingSelectsNothing() {
        SearchTextInput input = new SearchTextInput();
        input.selectAll();
        assertFalse(input.hasSelection());
    }

    @Test
    void setCaretIsClampedAndNeverInsideAPair() {
        SearchTextInput input = typed("a😀b");
        input.setCaret(99);
        assertEquals(4, input.caret());
        input.setCaret(-5);
        assertEquals(0, input.caret());
        input.setCaret(2);
        assertEquals(1, input.caret());
    }
}
