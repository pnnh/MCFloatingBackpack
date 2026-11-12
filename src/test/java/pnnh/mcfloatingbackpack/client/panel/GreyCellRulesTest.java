package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/** 046: which cells are drawn grey while a search is on. */
class GreyCellRulesTest {
    @Test
    void aCellOfTheMainAreaWithoutASlotIsGreyWhileSearching() {
        assertTrue(GreyCellRules.greyed(true, false, PanelArea.MAIN, -1));
    }

    @Test
    void outsideASearchTheSameCellStaysPlainPanelBackground() {
        assertFalse(GreyCellRules.greyed(false, false, PanelArea.MAIN, -1));
    }

    @Test
    void aCellWithASlotIsNeverGrey() {
        for (int index : new int[] {0, 1, 35, 107}) {
            assertFalse(GreyCellRules.greyed(true, false, PanelArea.MAIN, index), "slot " + index);
            assertFalse(GreyCellRules.greyed(false, false, PanelArea.MAIN, index), "slot " + index);
        }
    }

    @Test
    void theToolAreasAreNeverGreyWhateverTheSearchSays() {
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.BOTTOM}) {
            assertFalse(GreyCellRules.greyed(true, false, area, -1), area.name());
            assertFalse(GreyCellRules.greyed(true, true, area, -1), area.name());
            assertFalse(GreyCellRules.greyed(false, false, area, -1), area.name());
        }
    }

    @Test
    void aMainAreaCoveredByAnotherPanelDrawsNoGreyCells() {
        assertFalse(GreyCellRules.greyed(true, true, PanelArea.MAIN, -1));
    }

    @Test
    void everyNegativeIndexCountsAsNoSlot() {
        assertTrue(GreyCellRules.greyed(true, false, PanelArea.MAIN, -1));
        assertTrue(GreyCellRules.greyed(true, false, PanelArea.MAIN, -5));
        assertTrue(GreyCellRules.greyed(true, false, PanelArea.MAIN, Integer.MIN_VALUE));
    }
}
