package pnnh.mcfloatingbackpack.light;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ToolLightsTest {
    /** What Blocks.java of 1.21.1 gives each block: the table must say the same, so that the order between the lights is the game's. */
    private static final Map<String, Integer> VANILLA = Map.ofEntries(
            Map.entry("minecraft:lantern", 15),
            Map.entry("minecraft:soul_lantern", 10),
            Map.entry("minecraft:sea_lantern", 15),
            Map.entry("minecraft:glowstone", 15),
            Map.entry("minecraft:shroomlight", 15),
            Map.entry("minecraft:ochre_froglight", 15),
            Map.entry("minecraft:verdant_froglight", 15),
            Map.entry("minecraft:pearlescent_froglight", 15),
            Map.entry("minecraft:end_rod", 14),
            Map.entry("minecraft:jack_o_lantern", 15));

    @Test
    void everyLightHasExactlyTheBrightnessTheOriginalGameGivesItsBlock() {
        assertEquals(VANILLA, ToolLights.table());
    }

    @Test
    void theOrderBetweenTheLightsIsTheGamesNotReversed() {
        // soul torch / soul lantern (10) are dimmer than torch (14) / lantern (15); the end rod (14) is not brighter than a lantern
        assertTrue(ToolLights.table().get("minecraft:soul_lantern") < ToolLights.table().get("minecraft:lantern"));
        assertTrue(ToolLights.table().get("minecraft:end_rod") <= ToolLights.table().get("minecraft:lantern"));
        assertTrue(ToolLights.table().get("minecraft:soul_lantern") < ToolLights.table().get("minecraft:end_rod"));
    }

    @Test
    void noValueIsAboveTheBrightestLightOfTheGame() {
        assertEquals(15, ToolLights.MAX_LEVEL);
        for (Map.Entry<String, Integer> entry : ToolLights.table().entrySet()) {
            assertTrue(entry.getValue() >= 1 && entry.getValue() <= ToolLights.MAX_LEVEL, entry.getKey());
        }
    }

    @Test
    void onlyTheSoulLanternStaysBelowTheLevelThatMeltsSnowAndIce() {
        for (Map.Entry<String, Integer> entry : ToolLights.table().entrySet()) {
            assertEquals(entry.getKey().equals("minecraft:soul_lantern"), entry.getValue() <= ToolLights.NO_MELT_LEVEL, entry.getKey());
        }
    }

    @Test
    void anItemOutsideTheTagIsNoLightWhateverItsEmission() {
        assertEquals(0, ToolLights.level("minecraft:torch", false, 14, true));
        assertEquals(0, ToolLights.level("minecraft:lantern", false, 15, true));
    }

    @Test
    void aTableItemInTheTagUsesTheTableNotTheBlock() {
        assertEquals(15, ToolLights.level("minecraft:lantern", true, 3, true));
        assertEquals(10, ToolLights.level("minecraft:soul_lantern", true, 15, true));
    }

    @Test
    void anOtherItemGetsTheEmissionOfItsBlockWithinTheGamesRange() {
        assertEquals(15, ToolLights.level("other:lamp", true, 15, true));
        assertEquals(14, ToolLights.level("other:lamp", true, 14, true));
        assertEquals(10, ToolLights.level("other:lamp", true, 10, true));
        assertEquals(1, ToolLights.level("other:lamp", true, 0, true), "a light that gives no light still gives the least");
        assertEquals(15, ToolLights.level("other:lamp", true, 40, true));
    }

    @Test
    void anOtherItemThatIsNotABlockGetsEight() {
        assertEquals(ToolLights.NON_BLOCK_LEVEL, ToolLights.level("other:wand", true, 0, false));
        assertEquals(8, ToolLights.level("other:wand", true, 15, false));
    }

    @Test
    void everyPossibleResultIsInRange() {
        for (int luminance = -3; luminance <= 20; luminance++) {
            int level = ToolLights.level("other:x", true, luminance, true);
            assertTrue(level >= 1 && level <= ToolLights.MAX_LEVEL, "luminance " + luminance);
        }
    }
}
