package pnnh.mcfloatingbackpack.tool.furnace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 055: the experience of a furnace, vanilla's rule: the whole part, plus one with the probability of the fraction. */
class FurnaceExperienceTest {
    @Test
    void theWholePartIsAlwaysPaid() {
        assertEquals(7, FurnaceExperience.orbs(10, 0.7F, 0.99));
        assertEquals(0, FurnaceExperience.orbs(0, 0.7F, 0.0));
        assertEquals(3, FurnaceExperience.orbs(3, 1.0F, 0.0));
    }

    @Test
    void theFractionIsPaidWithItsProbability() {
        // 3 * 0.7 = 2.1: a random number below 0.1 gets the extra one
        assertEquals(3, FurnaceExperience.orbs(3, 0.7F, 0.05));
        assertEquals(2, FurnaceExperience.orbs(3, 0.7F, 0.5));
    }

    @Test
    void noExperienceRecipeMakesNothing() {
        assertEquals(0, FurnaceExperience.orbs(64, 0.0F, 0.0));
    }
}
