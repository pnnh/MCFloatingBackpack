package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 042: the inside of the search box. */
class SearchBoxLayoutTest {
    @Test
    void threeColumnsStillShowThreeDigitsOfCount() {
        // the box of a 3 column panel is 23..101
        SearchBoxLayout layout = SearchBoxLayout.of(23, 101, 18);
        assertTrue(layout.hasCount());
        assertEquals(26, layout.textLeft());
        assertEquals(90, layout.clearLeft());
        assertEquals(70, layout.countLeft());
        assertEquals(68, layout.textRight());
        assertTrue(layout.textRight() - layout.textLeft() >= SearchBoxLayout.MIN_TEXT);
    }

    @Test
    void aNarrowBoxDropsTheCountButKeepsTheTextAndTheClearButton() {
        SearchBoxLayout layout = SearchBoxLayout.of(23, 80, 18);
        assertFalse(layout.hasCount());
        assertEquals(layout.clearLeft() - SearchBoxLayout.GAP, layout.textRight());
        assertEquals(80 - 1 - 10, layout.clearLeft());
        assertTrue(layout.textRight() > layout.textLeft());
    }

    @Test
    void aWideBoxShowsEverything() {
        SearchBoxLayout layout = SearchBoxLayout.of(23, 197, 6);
        assertTrue(layout.hasCount());
        assertEquals(layout.countLeft() - SearchBoxLayout.GAP, layout.textRight());
        assertTrue(layout.countLeft() + 6 <= layout.clearLeft());
    }

    @Test
    void noCountWidthMeansNoCount() {
        assertFalse(SearchBoxLayout.of(23, 197, 0).hasCount());
    }

    @Test
    void aBoxTooSmallForTextNeverGetsANegativeTextArea() {
        SearchBoxLayout layout = SearchBoxLayout.of(0, 12, 18);
        assertTrue(layout.textRight() >= layout.textLeft());
    }
}
