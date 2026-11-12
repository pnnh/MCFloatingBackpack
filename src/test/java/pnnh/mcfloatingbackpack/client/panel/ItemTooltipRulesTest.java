package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/** 045: when the panel shows the item tooltip of the cell under the mouse. */
class ItemTooltipRulesTest {
    private static boolean show(PanelArea area, boolean mainCovered, boolean cursor, boolean drag, boolean dialog, boolean item) {
        return ItemTooltipRules.shouldShow(area, mainCovered, cursor, drag, dialog, item);
    }

    @Test
    void aCellWithAnItemShowsItsTooltipInEveryAreaWhenNothingGetsInTheWay() {
        for (PanelArea area : PanelArea.values()) {
            assertTrue(show(area, false, false, false, false, true), area.name());
        }
    }

    @Test
    void anEmptyCellShowsNothing() {
        for (PanelArea area : PanelArea.values()) {
            assertFalse(show(area, false, false, false, false, false), area.name());
        }
    }

    @Test
    void aCursorHoldingAnItemShowsNothingLikeTheVanillaScreens() {
        for (PanelArea area : PanelArea.values()) {
            assertFalse(show(area, false, true, false, false, true), area.name());
        }
    }

    @Test
    void aDragDistributePreviewShowsNothing() {
        for (PanelArea area : PanelArea.values()) {
            assertFalse(show(area, false, false, true, false, true), area.name());
        }
    }

    @Test
    void aQuestionWaitingForAnAnswerShowsNothing() {
        for (PanelArea area : PanelArea.values()) {
            assertFalse(show(area, false, false, false, true, true), area.name());
        }
    }

    @Test
    void aCoveredMainAreaShowsNothingButTheToolAreasStillDo() {
        assertFalse(show(PanelArea.MAIN, true, false, false, false, true));
        assertTrue(show(PanelArea.LEFT, true, false, false, false, true));
        assertTrue(show(PanelArea.RIGHT, true, false, false, false, true));
        assertTrue(show(PanelArea.BOTTOM, true, false, false, false, true));
    }

    @Test
    void anyTwoReasonsAreStillNoTooltip() {
        assertFalse(show(PanelArea.MAIN, true, true, false, false, true));
        assertFalse(show(PanelArea.LEFT, false, true, true, false, true));
        assertFalse(show(PanelArea.BOTTOM, false, false, true, true, true));
        assertFalse(show(PanelArea.RIGHT, true, true, true, true, true));
    }
}
