package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TagTextInputTest {
    @Test
    void typingInsertsAcceptedCharactersAtTheCaret() {
        TagTextInput in = new TagTextInput();
        for (char c : "wood".toCharArray()) {
            in.type(c);
        }
        assertEquals("wood", in.text());
        assertEquals(4, in.caret());
        in.left();
        in.left();
        in.type('_');
        assertEquals("wo_od", in.text());
        assertEquals(3, in.caret());
    }

    @Test
    void rejectsUppercaseSpaceAndSymbolsOutsideTheRange() {
        TagTextInput in = new TagTextInput();
        in.type('W');
        in.type(' ');
        in.type('中');
        in.type('a');
        assertEquals("a", in.text());
    }

    @Test
    void stopsAtSixteenCharacters() {
        TagTextInput in = new TagTextInput();
        for (int i = 0; i < 30; i++) {
            in.type('a');
        }
        assertEquals(16, in.text().length());
    }

    @Test
    void pasteFiltersAndCutsToTheRoomLeft() {
        TagTextInput in = new TagTextInput();
        in.paste("Wood Axe!");
        assertEquals("oodxe!", in.text());
        in.paste("a".repeat(40));
        assertEquals(16, in.text().length());
        assertEquals(16, in.caret());
    }

    @Test
    void backspaceAndDeleteStayInRange() {
        TagTextInput in = new TagTextInput();
        in.backspace();
        in.delete();
        assertEquals("", in.text());
        in.paste("abc");
        in.backspace();
        assertEquals("ab", in.text());
        in.home();
        in.delete();
        assertEquals("b", in.text());
        in.delete();
        in.delete();
        assertEquals("", in.text());
        assertEquals(0, in.caret());
    }

    @Test
    void caretMovesWithinTheText() {
        TagTextInput in = new TagTextInput();
        in.paste("abc");
        in.home();
        in.left();
        assertEquals(0, in.caret());
        in.right();
        in.right();
        in.right();
        in.right();
        assertEquals(3, in.caret());
        in.home();
        in.end();
        assertEquals(3, in.caret());
    }

    @Test
    void clearEmptiesTheBuffer() {
        TagTextInput in = new TagTextInput();
        in.paste("abc");
        in.clear();
        assertEquals("", in.text());
        assertEquals(0, in.caret());
    }
}
