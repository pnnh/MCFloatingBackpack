package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.QuickTransferGesture.Hit;
import pnnh.mcfloatingbackpack.client.panel.QuickTransferGesture.Side;

class QuickTransferGestureTest {
    /** A row of 9 inventory slots, 18px apart starting at x=0, plus a panel row below it (y 100-116). */
    private static Hit resolve(double x, double y) {
        if (y >= 0 && y < 16 && x >= 0 && x < 9 * 18) {
            int col = (int) (x / 18);
            if (x - col * 18 < 16) {
                return new Hit(Side.INVENTORY, col, col != 4); // slot 4 is empty
            }
            return null;
        }
        if (y >= 100 && y < 116 && x >= 0 && x < 9 * 18) {
            int col = (int) (x / 18);
            if (x - col * 18 < 16) {
                return new Hit(Side.PANEL, col, true);
            }
        }
        return null;
    }

    @Test
    void beginSendsTheStartSlotAndLocksDirection() {
        QuickTransferGesture g = new QuickTransferGesture();
        assertFalse(g.isActive());
        List<Integer> first = g.begin(new Hit(Side.INVENTORY, 0, true), 8, 8);
        assertTrue(g.isActive());
        assertEquals(Side.INVENTORY, g.side());
        assertEquals(List.of(0), first);
    }

    @Test
    void startingOnAnEmptySlotSendsNothingButStillStartsTheDrag() {
        QuickTransferGesture g = new QuickTransferGesture();
        List<Integer> first = g.begin(new Hit(Side.INVENTORY, 4, false), 4 * 18 + 8, 8);
        assertTrue(first.isEmpty());
        assertTrue(g.isActive());
        // slot 5 has an item and is next to the empty start
        assertEquals(List.of(5), g.advance(QuickTransferGestureTest::resolve, 5 * 18 + 8, 8));
    }

    @Test
    void eachSlotIsReportedOnlyOncePerDrag() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.INVENTORY, 0, true), 8, 8);
        assertEquals(List.of(1), g.advance(QuickTransferGestureTest::resolve, 18 + 8, 8));
        // jitter back and forth over slots 0 and 1: nothing new
        assertTrue(g.advance(QuickTransferGestureTest::resolve, 8, 8).isEmpty());
        assertTrue(g.advance(QuickTransferGestureTest::resolve, 18 + 8, 8).isEmpty());
        assertTrue(g.advance(QuickTransferGestureTest::resolve, 18 + 9, 8).isEmpty());
    }

    @Test
    void fastMovesDoNotSkipSlotsBetweenFrames() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.INVENTORY, 0, true), 8, 8);
        // one frame jumps from slot 0 straight to slot 8: every slot on the way must be reported
        List<Integer> hits = g.advance(QuickTransferGestureTest::resolve, 8 * 18 + 8, 8);
        assertEquals(List.of(1, 2, 3, 5, 6, 7, 8), hits); // 4 is empty, so it is visited but not reported
    }

    @Test
    void directionIsFixedByTheStartSide() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.INVENTORY, 0, true), 8, 8);
        // sliding onto the panel row must not report panel slots
        assertTrue(g.advance(QuickTransferGestureTest::resolve, 8, 108).isEmpty());
        assertEquals(Side.INVENTORY, g.side());
    }

    @Test
    void panelStartOnlyCollectsPanelSlots() {
        QuickTransferGesture g = new QuickTransferGesture();
        assertEquals(List.of(2), g.begin(new Hit(Side.PANEL, 2, true), 2 * 18 + 8, 108));
        assertEquals(List.of(3), g.advance(QuickTransferGestureTest::resolve, 3 * 18 + 8, 108));
        assertTrue(g.advance(QuickTransferGestureTest::resolve, 3 * 18 + 8, 8).isEmpty());
    }

    @Test
    void stopEndsTheGestureAndForgetsEverything() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.INVENTORY, 0, true), 8, 8);
        g.stop();
        assertFalse(g.isActive());
        assertTrue(g.advance(QuickTransferGestureTest::resolve, 18 + 8, 8).isEmpty());
        // a new gesture starts clean: slot 0 is reported again
        assertEquals(List.of(0), g.begin(new Hit(Side.INVENTORY, 0, true), 8, 8));
    }
}
