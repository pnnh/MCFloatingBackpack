package pnnh.mcfloatingbackpack.tool.furnace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pnnh.mcfloatingbackpack.tool.furnace.FakeFurnaceRules.EMPTY;
import static pnnh.mcfloatingbackpack.tool.furnace.FakeFurnaceRules.RULES;
import static pnnh.mcfloatingbackpack.tool.furnace.FakeFurnaceRules.s;

import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.tool.furnace.FakeFurnaceRules.Stack;

/** 055: the engine's few-at-a-time fast forward gives exactly what ticking that many times gives, and it does its work by events, not by ticks. */
class FurnaceEngineTest {
    private static FurnaceCore<Stack> core(Stack in, Stack fuel, Stack out) {
        return new FurnaceCore<>(in, fuel, out);
    }

    private static void ticks(FurnaceCore<Stack> c, long n) {
        for (long i = 0; i < n; i++) {
            FurnaceEngine.tickOnce(c, RULES);
        }
    }

    private static void assertSame(FurnaceCore<Stack> expected, FurnaceCore<Stack> actual, String what) {
        assertEquals(expected.input, actual.input, what + " input");
        assertEquals(expected.fuel, actual.fuel, what + " fuel");
        assertEquals(expected.output, actual.output, what + " output");
        assertEquals(expected.litTime, actual.litTime, what + " litTime");
        assertEquals(expected.litDuration, actual.litDuration, what + " litDuration");
        assertEquals(expected.cookProgress, actual.cookProgress, what + " cookProgress");
        assertEquals(expected.cookTotal, actual.cookTotal, what + " cookTotal");
        assertEquals(expected.recipesUsed, actual.recipesUsed, what + " recipesUsed");
    }

    private static void both(FurnaceCore<Stack> start, long n, String what) {
        FurnaceCore<Stack> slow = start.copy(RULES);
        FurnaceCore<Stack> fast = start.copy(RULES);
        ticks(slow, n);
        FurnaceEngine.advance(fast, n, RULES);
        assertSame(slow, fast, what);
    }

    @Test
    void oneItemOnOneCoalTakesTwoHundredTicksAndTheFuelIsUsedUp() {
        FurnaceCore<Stack> c = core(s("iron_ore", 1), s("coal", 1), EMPTY);
        c.cookTotal = 200;
        FurnaceEngine.advance(c, 200, RULES);
        assertEquals(s("iron_ingot", 1), c.output);
        assertEquals(EMPTY, c.input);
        assertEquals(EMPTY, c.fuel);
        assertEquals(1, c.recipesUsed.get("smelt_iron"));
        assertEquals(1600 - 199, c.litTime);
    }

    @Test
    void theFirstTickLightsTheFuel() {
        FurnaceCore<Stack> c = core(s("iron_ore", 4), s("coal", 2), EMPTY);
        c.cookTotal = 200;
        FurnaceEngine.advance(c, 1, RULES);
        assertEquals(1600, c.litTime);
        assertEquals(1600, c.litDuration);
        assertEquals(s("coal", 1), c.fuel);
        assertEquals(1, c.cookProgress);
    }

    @Test
    void aLavaBucketLeavesABucketAndTheFuelBurnsAfterTheInputIsGone() {
        FurnaceCore<Stack> c = core(s("iron_ore", 1), s("lava_bucket", 1), EMPTY);
        c.cookTotal = 200;
        FurnaceEngine.advance(c, 500, RULES);
        assertEquals(s("bucket", 1), c.fuel);
        assertEquals(s("iron_ingot", 1), c.output);
        assertEquals(20000 - 499, c.litTime);
    }

    @Test
    void aFuelThatRunsOutMidwayStopsAndTheProgressFallsBack() {
        FurnaceCore<Stack> c = core(s("iron_ore", 8), s("stick", 1), EMPTY);
        c.cookTotal = 200;
        both(c, 100, "just out of fuel");
        both(c, 101, "after the fuel");
        both(c, 150, "falling back");
        both(c, 5000, "long after");
        FurnaceCore<Stack> done = c.copy(RULES);
        FurnaceEngine.advance(done, 5000, RULES);
        assertEquals(0, done.litTime);
        assertEquals(0, done.cookProgress);
        assertEquals(s("iron_ore", 8), done.input);
    }

    @Test
    void aFullOutputStopsTheSmelting() {
        FurnaceCore<Stack> c = core(s("iron_ore", 5), s("coal", 1), s("iron_ingot", 63));
        c.cookTotal = 200;
        FurnaceEngine.advance(c, 100000, RULES);
        assertEquals(s("iron_ingot", 64), c.output);
        assertEquals(s("iron_ore", 4), c.input);
    }

    @Test
    void anOutputOfAnotherKindBlocksTheSmelting() {
        FurnaceCore<Stack> c = core(s("iron_ore", 5), s("coal", 1), s("glass", 3));
        c.cookTotal = 200;
        FurnaceEngine.advance(c, 100000, RULES);
        assertEquals(s("glass", 3), c.output);
        assertEquals(s("iron_ore", 5), c.input);
    }

    @Test
    void aWetSpongeNextToAnEmptyBucketMakesAWaterBucket() {
        FurnaceCore<Stack> c = core(s("wet_sponge", 1), s("bucket", 1), EMPTY);
        c.cookTotal = 50;
        c.litTime = 1000;
        c.litDuration = 1000;
        FurnaceEngine.advance(c, 60, RULES);
        assertEquals(s("sponge", 1), c.output);
        assertEquals(s("water_bucket", 1), c.fuel);
    }

    @Test
    void noTimeChangesNothingAndAnEmptyFurnaceStaysEmpty() {
        FurnaceCore<Stack> c = core(s("iron_ore", 3), s("coal", 1), EMPTY);
        both(c, 0, "no time");
        FurnaceCore<Stack> empty = core(EMPTY, EMPTY, EMPTY);
        both(empty, 1_000_000, "empty");
    }

    @Test
    void aMillionTicksIsAFewIterations() {
        // 64 ores on 3 coal blocks' worth of coal: finishes long before the million ticks are up
        FurnaceCore<Stack> c = core(s("iron_ore", 64), s("coal", 64), EMPTY);
        c.cookTotal = 200;
        long start = System.nanoTime();
        FurnaceEngine.advance(c, 1_000_000_000L, RULES);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(s("iron_ingot", 64), c.output);
        assertTrue(millis < 500, "took " + millis + " ms");
        assertEquals(64, c.recipesUsed.get("smelt_iron"));
    }

    @Test
    void fastForwardEqualsTickingOnManyRandomFurnaces() {
        Random random = new Random(55);
        String[] inputs = {"iron_ore", "sand", "wet_sponge", "ore_pair", "stone"};
        String[] fuels = {"coal", "stick", "lava_bucket", "plank", "bucket", "dirt"};
        String[] outputs = {"iron_ingot", "glass", "sponge", "ingot_pair", "dirt"};
        int[] totals = {0, 50, 100, 200};
        for (int round = 0; round < 6000; round++) {
            Stack in = random.nextInt(6) == 0 ? EMPTY : s(inputs[random.nextInt(inputs.length)], 1 + random.nextInt(random.nextBoolean() ? 3 : 64));
            Stack fuel = random.nextInt(6) == 0 ? EMPTY : s(fuels[random.nextInt(fuels.length)], 1 + random.nextInt(random.nextBoolean() ? 2 : 64));
            Stack out = random.nextInt(2) == 0 ? EMPTY : s(outputs[random.nextInt(outputs.length)], 1 + random.nextInt(random.nextBoolean() ? 5 : 64));
            FurnaceCore<Stack> c = core(in, fuel, out);
            c.cookTotal = totals[random.nextInt(totals.length)];
            c.cookProgress = c.cookTotal == 0 ? random.nextInt(3) : random.nextInt(c.cookTotal + 1);
            if (random.nextInt(3) == 0) {
                c.litTime = random.nextInt(random.nextBoolean() ? 4 : 2500);
                c.litDuration = Math.max(c.litTime, 100);
            }
            c.recipesUsed.put("smelt_iron", random.nextInt(3));
            long n = random.nextInt(4) == 0 ? random.nextInt(30) : random.nextInt(6000);
            both(c, n, "round " + round + " n=" + n + " from " + c.input + "/" + c.fuel + "/" + c.output + " L=" + c.litTime + " P=" + c.cookProgress
                    + " T=" + c.cookTotal);
        }
    }

    @Test
    void advancingInSeveralStepsEqualsAdvancingAtOnce() {
        Random random = new Random(7);
        for (int round = 0; round < 500; round++) {
            FurnaceCore<Stack> start = core(s("iron_ore", 1 + random.nextInt(20)), s("stick", 1 + random.nextInt(10)), EMPTY);
            start.cookTotal = 200;
            FurnaceCore<Stack> once = start.copy(RULES);
            FurnaceCore<Stack> steps = start.copy(RULES);
            long total = 0;
            for (int i = 0; i < 6; i++) {
                long n = random.nextInt(1500);
                FurnaceEngine.advance(steps, n, RULES);
                total += n;
            }
            FurnaceEngine.advance(once, total, RULES);
            assertSame(once, steps, "round " + round);
        }
    }
}
