package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.ToolSingleRules.Verdict;

class ToolSingleRulesTest {
    // a stack is its name; the kind starts with "jukebox", the empty cell is ""
    private static final Predicate<String> JUKEBOX = s -> s.startsWith("jukebox");

    private static Verdict check(PanelArea area, String incoming, int slot, List<String> slots) {
        return ToolSingleRules.check(PanelArea.RIGHT, area, incoming, slot, slots, JUKEBOX);
    }

    @Test
    void theFirstOneInTheRightAreaIsFine() {
        assertEquals(Verdict.OK, check(PanelArea.RIGHT, "jukebox", 0, List.of("", "trash_can", "")));
    }

    @Test
    void aSecondOneIsRefused() {
        assertEquals(Verdict.DUPLICATE, check(PanelArea.RIGHT, "jukebox", 2, List.of("jukebox", "", "")));
    }

    @Test
    void replacingTheOneInTheSameSlotIsFine() {
        assertEquals(Verdict.OK, check(PanelArea.RIGHT, "jukebox", 0, List.of("jukebox", "", "")));
    }

    @Test
    void onlyTheTargetAreaCounts() {
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.BOTTOM, PanelArea.MAIN}) {
            assertEquals(Verdict.OK, check(area, "jukebox", 1, List.of("jukebox", "", "")), area.name());
        }
    }

    @Test
    void anotherToolIsAlwaysFine() {
        assertEquals(Verdict.OK, check(PanelArea.RIGHT, "trash_can", 1, List.of("jukebox", "", "")));
    }

    @Test
    void aBackpackThatAlreadyHoldsTwoKeepsThemButGetsNoThird() {
        List<String> two = List.of("jukebox", "jukebox", "");
        assertEquals(Verdict.DUPLICATE, check(PanelArea.RIGHT, "jukebox", 2, two));
        assertEquals(Verdict.OK, check(PanelArea.RIGHT, "jukebox", 0, two), "put over one of the two: the count stays");
    }

    @Test
    void theOtherAreasLimitsAreNotThisOnesBusiness() {
        // the right area also holds the one trash can; that is another rule, so a jukebox next to a trash can is fine
        assertEquals(Verdict.OK, check(PanelArea.RIGHT, "jukebox", 1, List.of("trash_can", "", "")));
    }
}
