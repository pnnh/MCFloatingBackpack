package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.QuickTransferGesture.Hit;
import pnnh.mcfloatingbackpack.client.panel.QuickTransferGesture.Side;

class QuickTransferGestureTest {
    /**
     * Layout (GUI px): a container row at y 0-16 (menu slots 10..18, slot 14 empty), a player-inventory
     * row at y 40-56 (inventory slots 0..8), and a panel row at y 100-116 (panel slots 0..8). All 18 px apart.
     */
    private static Hit resolve(double x, double y) {
        if (x < 0 || x >= 9 * 18) {
            return null;
        }
        int col = (int) (x / 18);
        if (x - col * 18 >= 16) {
            return null;
        }
        if (y >= 0 && y < 16) {
            return new Hit(Side.CONTAINER, 10 + col, col != 4);
        }
        if (y >= 40 && y < 56) {
            return new Hit(Side.INVENTORY, col, true);
        }
        if (y >= 100 && y < 116) {
            return new Hit(Side.PANEL, col, true);
        }
        return null;
    }

    private static List<Integer> idx(List<Hit> hits) {
        return hits.stream().map(Hit::index).toList();
    }

    private static List<Hit> advance(QuickTransferGesture g, double x, double y) {
        return g.advance(QuickTransferGestureTest::resolve, x, y);
    }

    @Test
    void beginReportsTheStartSlot() {
        QuickTransferGesture g = new QuickTransferGesture();
        assertFalse(g.isActive());
        List<Hit> first = g.begin(new Hit(Side.INVENTORY, 0, true), 8, 48);
        assertTrue(g.isActive());
        assertEquals(Side.INVENTORY, g.side());
        assertEquals(List.of(0), idx(first));
    }

    @Test
    void startingOnAnEmptySlotReportsNothingButStillStartsTheDrag() {
        QuickTransferGesture g = new QuickTransferGesture();
        assertTrue(g.begin(new Hit(Side.CONTAINER, 14, false), 4 * 18 + 8, 8).isEmpty());
        assertTrue(g.isActive());
        assertEquals(List.of(15), idx(advance(g, 5 * 18 + 8, 8)));
    }

    @Test
    void eachSlotIsReportedOnlyOncePerDrag() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.CONTAINER, 10, true), 8, 8);
        assertEquals(List.of(11), idx(advance(g, 18 + 8, 8)));
        assertTrue(advance(g, 8, 8).isEmpty());
        assertTrue(advance(g, 18 + 8, 8).isEmpty());
        assertTrue(advance(g, 18 + 9, 8).isEmpty());
    }

    @Test
    void fastMovesDoNotSkipSlotsBetweenFrames() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.CONTAINER, 10, true), 8, 8);
        List<Hit> hits = advance(g, 8 * 18 + 8, 8);
        assertEquals(List.of(11, 12, 13, 15, 16, 17, 18), idx(hits)); // 14 is empty: visited, not reported
    }

    @Test
    void containerAndInventorySlotsBelongToTheSameIntoBackpackGroup() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.CONTAINER, 10, true), 8, 8);
        // sliding down onto the player-inventory row collects those too (both go into the backpack)
        List<Hit> hits = advance(g, 8, 48);
        assertTrue(hits.stream().anyMatch(h -> h.side() == Side.INVENTORY && h.index() == 0));
        assertTrue(hits.stream().noneMatch(h -> h.side() == Side.PANEL));
    }

    @Test
    void indicesOfDifferentSidesDoNotCollideInTheVisitedSet() {
        QuickTransferGesture g = new QuickTransferGesture();
        // inventory slot 3 and container menu slot 3 are different slots with the same number
        g.begin(new Hit(Side.INVENTORY, 3, true), 3 * 18 + 8, 48);
        List<Hit> hits = g.advance((x, y) -> y < 20 ? new Hit(Side.CONTAINER, 3, true) : null, 3 * 18 + 8, 8);
        assertEquals(1, hits.size());
        assertEquals(Side.CONTAINER, hits.get(0).side());
    }

    @Test
    void startingOnThePanelOnlyCollectsPanelSlots() {
        QuickTransferGesture g = new QuickTransferGesture();
        assertEquals(List.of(2), idx(g.begin(new Hit(Side.PANEL, 2, true), 2 * 18 + 8, 108)));
        assertEquals(List.of(3), idx(advance(g, 3 * 18 + 8, 108)));
        assertTrue(advance(g, 3 * 18 + 8, 8).isEmpty()); // container row ignored
        assertTrue(advance(g, 3 * 18 + 8, 48).isEmpty()); // inventory row ignored
    }

    @Test
    void intoBackpackDragIgnoresThePanelRow() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.INVENTORY, 0, true), 8, 48);
        assertTrue(advance(g, 8, 108).stream().noneMatch(h -> h.side() == Side.PANEL));
    }

    @Test
    void stopEndsTheGestureAndForgetsEverything() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.CONTAINER, 10, true), 8, 8);
        g.stop();
        assertFalse(g.isActive());
        assertTrue(advance(g, 18 + 8, 8).isEmpty());
        assertEquals(List.of(10), idx(g.begin(new Hit(Side.CONTAINER, 10, true), 8, 8)));
    }

    /** 021: a trash row at y 200-216 (trash slots 0..8, slot 4 empty). */
    private static Hit resolveWithTrash(double x, double y) {
        if (y >= 200 && y < 216 && x >= 0 && x < 9 * 18 && x - (int) (x / 18) * 18 < 16) {
            int col = (int) (x / 18);
            return new Hit(Side.TRASH, col, col != 4);
        }
        return resolve(x, y);
    }

    private static List<Hit> advanceWithTrash(QuickTransferGesture g, double x, double y) {
        return g.advance(QuickTransferGestureTest::resolveWithTrash, x, y);
    }

    @Test
    void trashDragOnlyAcceptsTrashSlots() {
        QuickTransferGesture g = new QuickTransferGesture();
        assertEquals(List.of(0), idx(g.begin(new Hit(Side.TRASH, 0, true), 8, 208)));
        assertTrue(advanceWithTrash(g, 8, 108).isEmpty()); // panel row
        assertTrue(advanceWithTrash(g, 8, 48).isEmpty()); // inventory row
        assertTrue(advanceWithTrash(g, 8, 8).isEmpty()); // container row
        // the straight path from the container row reaches the trash row only at its far end, over slot 2
        assertEquals(List.of(2), idx(advanceWithTrash(g, 2 * 18 + 8, 208)));
    }

    @Test
    void otherDragsIgnoreTrashSlots() {
        for (Hit start : List.of(new Hit(Side.INVENTORY, 0, true), new Hit(Side.CONTAINER, 10, true), new Hit(Side.PANEL, 0, true))) {
            QuickTransferGesture g = new QuickTransferGesture();
            g.begin(start, 8, 8);
            assertTrue(advanceWithTrash(g, 8, 208).stream().noneMatch(h -> h.side() == Side.TRASH), "start " + start.side());
        }
    }

    @Test
    void inventoryAndContainerStillShareAGroup() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.INVENTORY, 0, true), 8, 48);
        assertEquals(List.of(10), idx(advanceWithTrash(g, 8, 8)));
    }

    @Test
    void aTrashSlotIsReportedOnceAndEmptyOnesAreSkipped() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.TRASH, 3, true), 3 * 18 + 8, 208);
        assertEquals(List.of(5), idx(advanceWithTrash(g, 5 * 18 + 8, 208))); // 4 is empty
        assertTrue(advanceWithTrash(g, 3 * 18 + 8, 208).isEmpty()); // 3 was the start
    }

    @Test
    void trashSlotsAreNotTheSameSlotsAsPanelSlotsWithTheSameIndex() {
        QuickTransferGesture g = new QuickTransferGesture();
        g.begin(new Hit(Side.TRASH, 0, true), 8, 208);
        assertTrue(advanceWithTrash(g, 8, 108).isEmpty());
        assertTrue(advanceWithTrash(g, 8, 208).isEmpty()); // back on trash slot 0: already visited
    }
}
