package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TitleDotLayoutTest {
    @Test
    void theDotEndsBeforeWhereTheBackpackNumberStarts() {
        // The number starts at panelX + ICON_OFFSET + ICON_SIZE + GAP (TitleIdLayout.of with an x of 0 and room to spare).
        int numberStart = TitleIdLayout.of(0, 1000, 10).x();
        int dotRight = TitleDotLayout.left(0) + TitleDotLayout.SIZE + TitleDotLayout.HOVER_MARGIN;
        assertTrue(dotRight <= numberStart, "the hover area " + dotRight + " must stop before the number at " + numberStart);
    }

    @Test
    void theDotSitsOnTheTopRightCornerOfTheIcon() {
        int iconLeft = TitleIdLayout.ICON_OFFSET;
        int iconRight = iconLeft + TitleIdLayout.ICON_SIZE;
        assertEquals(iconRight - TitleDotLayout.SIZE + 1, TitleDotLayout.left(0));
        assertTrue(TitleDotLayout.left(0) >= iconLeft);
        assertTrue(TitleDotLayout.left(0) + TitleDotLayout.SIZE <= iconRight + 1);
        assertEquals(1, TitleDotLayout.top(0));
    }

    @Test
    void itMovesWithThePanel() {
        assertEquals(TitleDotLayout.left(0) + 100, TitleDotLayout.left(100));
        assertEquals(TitleDotLayout.top(0) + 50, TitleDotLayout.top(50));
    }

    @Test
    void theHoverAreaContainsTheDotAndIsOneMarginBigger() {
        int x = TitleDotLayout.left(10);
        int y = TitleDotLayout.top(20);
        assertTrue(TitleDotLayout.hits(10, 20, x, y));
        assertTrue(TitleDotLayout.hits(10, 20, x + TitleDotLayout.SIZE - 1, y + TitleDotLayout.SIZE - 1));
        assertTrue(TitleDotLayout.hits(10, 20, x + TitleDotLayout.SIZE, y + TitleDotLayout.SIZE));
        assertFalse(TitleDotLayout.hits(10, 20, x - 2, y));
        assertFalse(TitleDotLayout.hits(10, 20, x + TitleDotLayout.SIZE + 1, y));
        assertFalse(TitleDotLayout.hits(10, 20, x, y + TitleDotLayout.SIZE + 1));
    }

    @Test
    void theHoverAreaNeverReachesAboveThePanel() {
        // The dot's top is one pixel below the panel's top; its margin would be the panel's top row at most.
        assertFalse(TitleDotLayout.hits(10, 20, TitleDotLayout.left(10), 19));
        assertTrue(TitleDotLayout.hits(10, 20, TitleDotLayout.left(10), 20));
    }
}
