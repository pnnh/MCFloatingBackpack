package pnnh.mcfloatingbackpack.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.tank.HiddenLiquids.Line;

/** 048 (US6): a liquid with something in it and no tank that counts for it is hidden. */
class HiddenLiquidsTest {
    private static FluidStore store(Object... pairs) {
        Map<FluidKind, Integer> amounts = new java.util.EnumMap<>(FluidKind.class);
        for (int i = 0; i < pairs.length; i += 2) {
            amounts.put((FluidKind) pairs[i], (Integer) pairs[i + 1]);
        }
        return FluidStore.of(amounts, "");
    }

    private static List<Line> lines(FluidStore store, FluidKind... withTank) {
        Set<FluidKind> tanks = withTank.length == 0 ? EnumSet.noneOf(FluidKind.class) : EnumSet.copyOf(List.of(withTank));
        return HiddenLiquids.lines(store, tanks::contains);
    }

    @Test
    void nothingStoredIsNothingHidden() {
        assertTrue(lines(FluidStore.EMPTY).isEmpty());
    }

    @Test
    void aLiquidWithNoTankIsHidden() {
        assertEquals(List.of(new Line(FluidKind.WATER, 8000)), lines(store(FluidKind.WATER, 8000)));
    }

    @Test
    void aTankForTheLiquidMakesItNotHidden() {
        FluidStore both = store(FluidKind.WATER, 8000, FluidKind.OIL, 4000);
        assertEquals(List.of(new Line(FluidKind.OIL, 4000)), lines(both, FluidKind.WATER));
        assertTrue(lines(both, FluidKind.WATER, FluidKind.OIL).isEmpty());
    }

    @Test
    void theOrderIsTheOrderOfTheLiquids() {
        FluidStore all = store(FluidKind.HONEY, 250, FluidKind.WATER, 1, FluidKind.MILK, 7);
        assertEquals(
                List.of(new Line(FluidKind.WATER, 1), new Line(FluidKind.MILK, 7), new Line(FluidKind.HONEY, 250)), lines(all));
    }

    @Test
    void aTankForAnotherLiquidDoesNotHelp() {
        assertEquals(1, lines(store(FluidKind.LAVA, 1000), FluidKind.WATER, FluidKind.OIL).size());
    }
}
