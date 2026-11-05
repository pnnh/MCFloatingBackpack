package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TagRulesTest {
    @Test
    void acceptsLowercaseDigitsAndVisibleSymbols() {
        for (char c : "abcxyz0189_-.!~#@".toCharArray()) {
            assertTrue(TagRules.acceptsChar(c), String.valueOf(c));
        }
    }

    @Test
    void rejectsUppercaseSpaceControlAndNonAscii() {
        for (char c : new char[] {'A', 'M', 'Z', ' ', '\t', '\n', 'é', '中', (char) 127}) {
            assertFalse(TagRules.acceptsChar(c), "0x" + Integer.toHexString(c));
        }
    }

    @Test
    void lengthBounds() {
        assertFalse(TagRules.isValid(""));
        assertFalse(TagRules.isValid(null));
        assertTrue(TagRules.isValid("a"));
        assertTrue(TagRules.isValid("a".repeat(16)));
        assertFalse(TagRules.isValid("a".repeat(17)));
    }

    @Test
    void invalidCharactersMakeATagInvalid() {
        assertFalse(TagRules.isValid("Wood"));
        assertFalse(TagRules.isValid("a b"));
        assertFalse(TagRules.isValid("中文"));
        assertTrue(TagRules.isValid("my_tag-1"));
    }

    @Test
    void filterKeepsAcceptedCharactersAndCutsToTheMaximum() {
        assertEquals("oodxe!", TagRules.filter("Wood Axe!"));
        assertEquals("a".repeat(16), TagRules.filter("a".repeat(40)));
        assertEquals("", TagRules.filter("ABC DEF"));
        assertEquals("", TagRules.filter(null));
    }
}
