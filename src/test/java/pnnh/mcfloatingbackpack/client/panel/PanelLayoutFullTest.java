package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/** 066: on the "all" tab the main area is the whole inside of the panel: no side columns, no bottom row, no thick frame. */
class PanelLayoutFullTest {
    private static PanelLayout full(int cols, int rows, int[] map) {
        return PanelLayout.ofFull(10, 20, cols, rows, new int[] {map.length, 0, 0, 0}, new int[] {0, 0, 0, 0}, map);
    }

    private static int[] ascending(int n) {
        int[] map = new int[n];
        for (int i = 0; i < n; i++) {
            map[i] = i;
        }
        return map;
    }

    @Test
    void thePanelKeepsItsSizeAndTheGridFillsTheInside() {
        PanelLayout plain = PanelLayout.of(10, 20, 9, 4, new int[] {100, 4, 4, 11}, new int[] {0, 0, 0, 0});
        PanelLayout full = full(9, 4, ascending(100));
        assertEquals(plain.width(), full.width());
        assertEquals(plain.height(), full.height());
        // the inside of the panel: from the left pad to the right pad, from the top pad to the bottom pad
        PanelLayout.Rect main = full.rect(PanelArea.MAIN);
        assertEquals(10 + PanelLayout.PAD_X, main.x());
        assertEquals(20 + PanelLayout.PAD_TOP, main.y());
        assertEquals((9 + 2) * PanelLayout.SLOT, main.w());
        assertEquals((4 + 1) * PanelLayout.SLOT, main.h());
        assertEquals(full.width(), 2 * PanelLayout.PAD_X + main.w());
        assertEquals(full.height(), PanelLayout.PAD_TOP + main.h() + PanelLayout.PAD_BOTTOM);
    }

    @Test
    void theOtherAreasAreNotThere() {
        PanelLayout full = full(9, 4, ascending(100));
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.BOTTOM}) {
            assertEquals(0, full.visibleCount(area));
            assertEquals(0, full.rect(area).w());
        }
        PanelLayout.Rect main = full.rect(PanelArea.MAIN);
        // where the right column used to be is now a main cell
        PanelLayout.Hit hit = full.hit(main.x() + 10 * PanelLayout.SLOT + 3, main.y() + 3);
        assertNotNull(hit);
        assertEquals(PanelArea.MAIN, hit.area());
        assertEquals(10, hit.index());
    }

    @Test
    void cellsAreNumberedOverTheWiderGridAndScrollByTheTallerOne() {
        PanelLayout full = full(9, 4, ascending(11 * 5 * 2));
        assertEquals(11, full.mainCols());
        assertEquals(5, full.mainRows());
        assertEquals(55, full.visibleCount(PanelArea.MAIN));
        assertEquals(10, full.mainTotalRows());
        assertEquals(5, full.maxScroll(PanelArea.MAIN));
        assertEquals(11, full.slotIndex(PanelArea.MAIN, 11));
        PanelLayout.Rect main = full.rect(PanelArea.MAIN);
        assertEquals(main.x() + 2 * PanelLayout.SLOT, full.cellX(PanelArea.MAIN, 2));
        assertEquals(main.y() + PanelLayout.SLOT, full.cellY(PanelArea.MAIN, 11));
    }

    @Test
    void aFillerCellOfTheMapIsNoHit() {
        int[] map = {0, 1, -1, 2};
        PanelLayout full = full(1, 1, map);
        PanelLayout.Rect main = full.rect(PanelArea.MAIN);
        assertNull(full.hit(main.x() + 2 * PanelLayout.SLOT + 2, main.y() + 2));
    }

    @Test
    void theOrdinaryLayoutIsNotChanged() {
        PanelLayout plain = PanelLayout.of(0, 0, 9, 4, new int[] {100, 4, 4, 11}, new int[] {0, 0, 0, 0});
        assertEquals(9, plain.mainCols());
        assertEquals(4, plain.mainRows());
        assertEquals(PanelLayout.PAD_X + PanelLayout.SLOT, plain.rect(PanelArea.MAIN).x());
    }
}
