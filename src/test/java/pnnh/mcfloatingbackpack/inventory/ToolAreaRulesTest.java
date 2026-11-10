package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/** 021/024: at most one trash can in the right area, one tag manager in the bottom area and one auto pickup in the left area; nothing else is limited. */
class ToolAreaRulesTest {
    private static final Predicate<String> TRASH = "trash"::equals;
    private static final Predicate<String> TAGS = "tags"::equals;
    private static final Predicate<String> AUTO = "auto"::equals;
    private static final String E = "";

    private static boolean can(PanelArea area, String incoming, int slot, String... slots) {
        List<String> list = Arrays.asList(slots);
        return ToolAreaRules.canPlace(area, incoming, slot, list, TRASH, TAGS, AUTO);
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
        assertTrue(can(PanelArea.MAIN, "auto", 1, "auto", E));
        assertTrue(can(PanelArea.RIGHT, "auto", 1, "auto", E));
        assertTrue(can(PanelArea.BOTTOM, "auto", 1, "auto", E));
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

    @Test
    void aSecondAutoPickupInTheLeftAreaIsRefused() {
        assertTrue(can(PanelArea.LEFT, "auto", 0, E, E, E));
        assertFalse(can(PanelArea.LEFT, "auto", 2, "auto", E, E));
        assertFalse(can(PanelArea.LEFT, "auto", 1, "auto", "dirt", E));
    }

    @Test
    void swappingOnTheAutoPickupsOwnSlotIsFine() {
        assertTrue(can(PanelArea.LEFT, "auto", 0, "auto", E, E));
    }

    @Test
    void leftoverAutoPickupsAreNeitherRemovedNorAllowedToGrow() {
        // a backpack that already has two auto pickups in the left area
        assertFalse(can(PanelArea.LEFT, "auto", 2, "auto", "auto", E));
        assertTrue(can(PanelArea.LEFT, "auto", 0, "auto", "auto", E));
        assertTrue(can(PanelArea.LEFT, "auto", 1, "auto", "auto", E));
    }

    @Test
    void theLeftAreaDoesNotLimitTheOtherTools() {
        assertTrue(can(PanelArea.LEFT, "trash", 2, "trash", E, E));
        assertTrue(can(PanelArea.LEFT, "tags", 2, "tags", E, E));
        assertTrue(can(PanelArea.LEFT, "dirt", 1, "auto", E));
    }

    /** 035: an extension pack is not one of the limited tools, so any number of them goes into any area. */
    @Test
    void extensionPacksAreNotLimitedPerArea() {
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.BOTTOM}) {
            assertTrue(can(area, "food_pack", 2, "food_pack", "food_pack", E));
            assertTrue(can(area, "food_pack", 0, "food_pack", "tool_pack", "food_pack"));
        }
    }
}
