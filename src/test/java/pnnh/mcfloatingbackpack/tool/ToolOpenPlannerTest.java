package pnnh.mcfloatingbackpack.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.tool.ToolOpenPlanner.Decision;

class ToolOpenPlannerTest {
    private static final MobileTool T = MobileTool.CRAFTING_TABLE;

    @Test
    void aToolTheServerDoesNotKnowIsIgnoredWhateverElse() {
        for (boolean inRight : new boolean[] {true, false}) {
            assertEquals(Decision.IGNORE, ToolOpenPlanner.decide(null, inRight, null));
            assertEquals(Decision.IGNORE, ToolOpenPlanner.decide(null, inRight, T));
        }
    }

    @Test
    void aToolTheBackpackDoesNotHaveInItsRightAreaIsIgnored() {
        assertEquals(Decision.IGNORE, ToolOpenPlanner.decide(T, false, null));
        assertEquals(Decision.IGNORE, ToolOpenPlanner.decide(T, false, T), "a menu of it that is open closes by its own validity check");
    }

    @Test
    void withTheToolInTheRightAreaAndNoMenuOfOursOpenItOpens() {
        assertEquals(Decision.OPEN, ToolOpenPlanner.decide(T, true, null));
    }

    @Test
    void withItsMenuAlreadyOpenAnotherClickClosesIt() {
        assertEquals(Decision.CLOSE, ToolOpenPlanner.decide(T, true, T));
    }

    @Test
    void everyCombinationHasAnAnswerAndOnlyTheRightOnesOpenOrClose() {
        MobileTool[] tools = {null, T};
        for (MobileTool tool : tools) {
            for (boolean inRight : new boolean[] {true, false}) {
                for (MobileTool open : tools) {
                    Decision d = ToolOpenPlanner.decide(tool, inRight, open);
                    boolean mayAct = tool != null && inRight;
                    assertEquals(mayAct, d != Decision.IGNORE, tool + " " + inRight + " " + open);
                    if (d == Decision.CLOSE) {
                        assertEquals(tool, open);
                    }
                }
            }
        }
    }
}
