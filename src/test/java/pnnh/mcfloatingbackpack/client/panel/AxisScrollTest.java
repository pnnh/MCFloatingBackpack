package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AxisScrollTest {
    @Test
    void clampKeepsTheOffsetInsideTheScrollableRange() {
        assertEquals(0, AxisScroll.clamp(-3, 10, 4));
        assertEquals(3, AxisScroll.clamp(3, 10, 4));
        assertEquals(6, AxisScroll.clamp(6, 10, 4));
        assertEquals(6, AxisScroll.clamp(99, 10, 4));
    }

    @Test
    void contentThatFitsCannotScroll() {
        assertEquals(0, AxisScroll.clamp(5, 4, 4));
        assertEquals(0, AxisScroll.clamp(5, 3, 4));
        assertEquals(0, AxisScroll.clamp(5, 0, 4));
        assertFalse(AxisScroll.canForward(0, 4, 4));
        assertFalse(AxisScroll.canBack(0));
    }

    @Test
    void byMovesAndStopsAtBothEnds() {
        assertEquals(1, AxisScroll.by(0, 1, 10, 4));
        assertEquals(6, AxisScroll.by(6, 1, 10, 4));
        assertEquals(0, AxisScroll.by(0, -1, 10, 4));
        assertEquals(5, AxisScroll.by(6, -1, 10, 4));
        assertEquals(6, AxisScroll.by(2, 100, 10, 4));
        assertEquals(0, AxisScroll.by(2, -100, 10, 4));
    }

    @Test
    void thereIsMoreContentOnTheSideWhereTheWindowIsNotAtTheEnd() {
        // start: only forward
        assertFalse(AxisScroll.canBack(0));
        assertTrue(AxisScroll.canForward(0, 10, 4));
        // middle: both
        assertTrue(AxisScroll.canBack(3));
        assertTrue(AxisScroll.canForward(3, 10, 4));
        // end: only back
        assertTrue(AxisScroll.canBack(6));
        assertFalse(AxisScroll.canForward(6, 10, 4));
    }

    @Test
    void whenTheTotalShrinksTheOffsetIsPulledBackWithoutBlankSpace() {
        int offset = 6; // window 6..9 of 10
        assertEquals(2, AxisScroll.clamp(offset, 6, 4)); // total shrank to 6: window 2..5
        assertEquals(0, AxisScroll.clamp(offset, 4, 4));
    }
}
