package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/** 021: at most one trash can in the right area and one tag manager in the bottom area, and nothing else is limited. */
class ToolAreaRulesTest {
    private static final Predicate<String> TRASH = "trash"::equals;
    private static final Predicate<String> TAGS = "tags"::equals;
    private static final String E = "";

    private static boolean can(PanelArea area, String incoming, int slot, String... slots) {
        List<String> list = Arrays.asList(slots);
        return ToolAreaRules.canPlace(area, incoming, slot, list, TRASH, TAGS);
    }

    @Test
    void trashCanIntoAnEmptyRightAreaIsFine() {
        assertTrue(can(PanelArea.RIGHT, "trash", 0, E, E, E));
    }

    @Test
    void aSecondTrashCanInTheRightAreaIsRefused() {
        assertFalse(can(PanelArea.RIGHT, "trash", 2, "trash", E, E));
        assertFalse(can(PanelArea.RIGHT, "trash", 1, "trash", "dirt", E));
    }

    @Test
    void swappingOnTheTrashCansOwnSlotIsFine() {
        assertTrue(can(PanelArea.RIGHT, "trash", 0, "trash", E, E));
    }

    @Test
    void aSecondTagManagerInTheBottomAreaIsRefusedAndTheSwapIsFine() {
        assertTrue(can(PanelArea.BOTTOM, "tags", 3, E, E, E, E));
        assertFalse(can(PanelArea.BOTTOM, "tags", 3, "tags", E, E, E));
        assertTrue(can(PanelArea.BOTTOM, "tags", 0, "tags", E, E, E));
    }

    @Test
    void leftoverDuplicatesAreNeitherRemovedNorAllowedToGrow() {
        // an 018 backpack with two tag managers in the bottom area
        assertFalse(can(PanelArea.BOTTOM, "tags", 2, "tags", "tags", E));
        assertTrue(can(PanelArea.BOTTOM, "tags", 0, "tags", "tags", E));
        assertTrue(can(PanelArea.BOTTOM, "tags", 1, "tags", "tags", E));
    }

    @Test
    void otherAreasAreNotLimited() {
        assertTrue(can(PanelArea.MAIN, "trash", 1, "trash", E));
        assertTrue(can(PanelArea.LEFT, "trash", 1, "trash", E));
        assertTrue(can(PanelArea.BOTTOM, "trash", 1, "trash", E));
        assertTrue(can(PanelArea.RIGHT, "tags", 1, "tags", E));
        assertTrue(can(PanelArea.MAIN, "tags", 1, "tags", E));
        assertTrue(can(PanelArea.LEFT, "tags", 1, "tags", E));
    }

    @Test
    void otherItemsAreNeverLimited() {
        assertTrue(can(PanelArea.RIGHT, "dirt", 1, "trash", E));
        assertTrue(can(PanelArea.BOTTOM, "dirt", 1, "tags", E));
    }

    @Test
    void anOutOfRangeSlotIsTreatedAsReplacingNothing() {
        assertFalse(can(PanelArea.RIGHT, "trash", 9, "trash", E));
        assertFalse(can(PanelArea.RIGHT, "trash", -1, "trash", E));
        assertTrue(can(PanelArea.RIGHT, "trash", 9, E, E));
    }
}
