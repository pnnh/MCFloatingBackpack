package pnnh.mcfloatingbackpack.light;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.light.ToolLightRules.Verdict;

class ToolLightRulesTest {
    // a stack is its name; lights start with "light", the empty cell is ""
    private static final Predicate<String> LIGHT = s -> s.startsWith("light");

    @Test
    void thePlayersFirstLightInTheBottomAreaIsFine() {
        assertEquals(Verdict.OK, ToolLightRules.check(PanelArea.BOTTOM, "light:a", 0, List.of("", "", ""), LIGHT));
    }

    @Test
    void aSecondLightIsRefusedWhateverItsKind() {
        assertEquals(Verdict.DUPLICATE, ToolLightRules.check(PanelArea.BOTTOM, "light:b", 1, List.of("light:a", "", ""), LIGHT));
        assertEquals(Verdict.DUPLICATE, ToolLightRules.check(PanelArea.BOTTOM, "light:a", 2, List.of("light:a", "", ""), LIGHT));
    }

    @Test
    void replacingTheLightWithAnotherInTheSameSlotIsFine() {
        assertEquals(Verdict.OK, ToolLightRules.check(PanelArea.BOTTOM, "light:b", 0, List.of("light:a", "", ""), LIGHT));
    }

    @Test
    void aLightPutOverAnOrdinaryItemWhileAnotherLightExistsIsRefused() {
        assertEquals(Verdict.DUPLICATE, ToolLightRules.check(PanelArea.BOTTOM, "light:b", 1, List.of("light:a", "tool", ""), LIGHT));
    }

    @Test
    void onlyTheBottomAreaCounts() {
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.MAIN}) {
            assertEquals(Verdict.OK, ToolLightRules.check(area, "light:b", 1, List.of("light:a", "", ""), LIGHT), area.name());
        }
    }

    @Test
    void anItemThatIsNotALightIsAlwaysFine() {
        assertEquals(Verdict.OK, ToolLightRules.check(PanelArea.BOTTOM, "tag_manager", 1, List.of("light:a", "", ""), LIGHT));
    }

    @Test
    void aBackpackThatAlreadyHoldsTwoKeepsThemButGetsNoThird() {
        List<String> two = List.of("light:a", "light:b", "");
        assertEquals(Verdict.DUPLICATE, ToolLightRules.check(PanelArea.BOTTOM, "light:c", 2, two, LIGHT));
        // putting a light over one of the two leaves the count where it was: allowed
        assertEquals(Verdict.OK, ToolLightRules.check(PanelArea.BOTTOM, "light:c", 0, two, LIGHT));
    }

    @Test
    void theFirstLightIsTheOneThatCounts() {
        assertEquals(-1, ToolLightRules.firstLight(List.of("", "tool"), LIGHT));
        assertEquals(1, ToolLightRules.firstLight(List.of("", "light:x", "light:y"), LIGHT));
    }
}
