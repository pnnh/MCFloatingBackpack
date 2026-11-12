package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/** 042: the main area showing only the slots a search found. */
class PanelLayoutMappingTest {
    private static final int[] COUNTS = {54, 3, 3, 9};

    private static PanelLayout plain(int cols, int rows, int scroll) {
        return PanelLayout.of(10, 20, cols, rows, COUNTS, new int[] {scroll, 0, 0, 0});
    }

    private static PanelLayout mapped(int cols, int rows, int scroll, int... map) {
        return PanelLayout.of(10, 20, cols, rows, COUNTS, new int[] {scroll, 0, 0, 0}, map);
    }

    @Test
    void aNullMapIsTheSameLayoutAsNoMapAtAll() {
        for (int cols : new int[] {3, 4, 9}) {
            for (int scroll : new int[] {0, 1, 3}) {
                PanelLayout a = plain(cols, 4, scroll);
                PanelLayout b = PanelLayout.of(10, 20, cols, 4, COUNTS, new int[] {scroll, 0, 0, 0}, null);
                assertEquals(a.slotCount(PanelArea.MAIN), b.slotCount(PanelArea.MAIN));
                assertEquals(a.mainTotalRows(), b.mainTotalRows());
                assertEquals(a.maxScroll(PanelArea.MAIN), b.maxScroll(PanelArea.MAIN));
                for (int ordinal = 0; ordinal < a.visibleCount(PanelArea.MAIN); ordinal++) {
                    assertEquals(a.slotIndex(PanelArea.MAIN, ordinal), b.slotIndex(PanelArea.MAIN, ordinal));
                }
            }
        }
    }

    @Test
    void theFoundSlotsArePackedFromTheTopLeft() {
        PanelLayout layout = mapped(3, 3, 0, 5, 9, 40, 41);
        assertEquals(5, layout.slotIndex(PanelArea.MAIN, 0));
        assertEquals(9, layout.slotIndex(PanelArea.MAIN, 1));
        assertEquals(40, layout.slotIndex(PanelArea.MAIN, 2));
        assertEquals(41, layout.slotIndex(PanelArea.MAIN, 3));
        assertEquals(-1, layout.slotIndex(PanelArea.MAIN, 4));
        assertEquals(-1, layout.slotIndex(PanelArea.MAIN, 8));
    }

    @Test
    void theAreaHasAsManySlotsAsTheMapHasEntries() {
        PanelLayout layout = mapped(3, 3, 0, 5, 9, 40, 41);
        assertEquals(4, layout.slotCount(PanelArea.MAIN));
        assertEquals(2, layout.mainTotalRows());
        assertEquals(0, layout.maxScroll(PanelArea.MAIN));
        // the tool areas are not touched
        assertEquals(3, layout.slotCount(PanelArea.LEFT));
        assertEquals(9, layout.slotCount(PanelArea.BOTTOM));
    }

    @Test
    void scrollMovesWholeRowsOfTheResults() {
        int[] map = new int[20];
        for (int i = 0; i < map.length; i++) {
            map[i] = i * 2;
        }
        PanelLayout layout = mapped(4, 3, 2, map);
        // 20 results over 4 columns: 5 rows, 3 shown, so the last scroll position is 2
        assertEquals(5, layout.mainTotalRows());
        assertEquals(2, layout.maxScroll(PanelArea.MAIN));
        assertEquals(map[8], layout.slotIndex(PanelArea.MAIN, 0));
        assertEquals(map[19], layout.slotIndex(PanelArea.MAIN, 11));
    }

    @Test
    void aMapWithNothingFoundShowsNoCellAndOneRow() {
        PanelLayout layout = mapped(3, 3, 0);
        assertEquals(0, layout.slotCount(PanelArea.MAIN));
        assertEquals(1, layout.mainTotalRows());
        for (int ordinal = 0; ordinal < 9; ordinal++) {
            assertEquals(-1, layout.slotIndex(PanelArea.MAIN, ordinal));
        }
    }

    @Test
    void aHitOnAShownCellGivesTheBackpackSlotNotTheCellNumber() {
        PanelLayout layout = mapped(3, 3, 0, 17, 33);
        PanelLayout.Rect main = layout.rect(PanelArea.MAIN);
        PanelLayout.Hit first = layout.hit(main.x() + 5, main.y() + 5);
        assertNotNull(first);
        assertEquals(PanelArea.MAIN, first.area());
        assertEquals(17, first.index());
        PanelLayout.Hit second = layout.hit(main.x() + PanelLayout.SLOT + 5, main.y() + 5);
        assertEquals(33, second.index());
        // a cell with nothing behind it is a filler: not a hit
        assertNull(layout.hit(main.x() + 2 * PanelLayout.SLOT + 5, main.y() + 5));
    }

    @Test
    void theToolAreasAreHitAsBefore() {
        PanelLayout layout = mapped(3, 3, 0, 17);
        PanelLayout.Rect left = layout.rect(PanelArea.LEFT);
        PanelLayout.Hit hit = layout.hit(left.x() + 5, left.y() + 5);
        assertEquals(PanelArea.LEFT, hit.area());
        assertEquals(0, hit.index());
    }
}
