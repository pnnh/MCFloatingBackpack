package pnnh.mcfloatingbackpack.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.tank.FluidKind;

/** 049: which pipe a fluid belongs to. */
class PipeKindTest {
    @Test
    void eachFluidGoesToItsOwnPipe() {
        assertEquals(PipeKind.WATER, PipeKind.classify(true, false, false));
        assertEquals(PipeKind.LAVA, PipeKind.classify(false, true, false));
        assertEquals(PipeKind.OIL, PipeKind.classify(false, false, true));
    }

    @Test
    void aFluidInNoTagBelongsToNoPipe() {
        assertNull(PipeKind.classify(false, false, false));
    }

    @Test
    void aFluidInSeveralTagsGoesToTheFirstOfWaterLavaOil() {
        assertEquals(PipeKind.WATER, PipeKind.classify(true, true, true));
        assertEquals(PipeKind.LAVA, PipeKind.classify(false, true, true));
    }

    @Test
    void aPipeAcceptsOnlyItsOwn() {
        assertTrue(PipeKind.WATER.accepts(true, false, false));
        assertFalse(PipeKind.WATER.accepts(false, true, false));
        assertFalse(PipeKind.OIL.accepts(true, false, false));
        assertFalse(PipeKind.OIL.accepts(false, false, false));
    }

    @Test
    void milkAndHoneyBelongToNoFluidInTheWorld() {
        for (boolean water : new boolean[] {false, true}) {
            for (boolean lava : new boolean[] {false, true}) {
                for (boolean oil : new boolean[] {false, true}) {
                    PipeKind kind = PipeKind.classify(water, lava, oil);
                    assertTrue(kind == null || kind.hasBlockForm());
                    assertFalse(PipeKind.MILK.accepts(water, lava, oil));
                    assertFalse(PipeKind.HONEY.accepts(water, lava, oil));
                }
            }
        }
    }

    @Test
    void thereAreFivePipesInTheCreativeOrder() {
        assertEquals(java.util.List.of(PipeKind.WATER, PipeKind.OIL, PipeKind.LAVA, PipeKind.MILK, PipeKind.HONEY), java.util.List.of(PipeKind.values()));
        assertEquals("milk_pipe", PipeKind.MILK.itemId());
        assertEquals("honey_pipe", PipeKind.HONEY.itemId());
    }

    @Test
    void theItemIdsAndLiquidsMatchTheTanks() {
        assertEquals("water_pipe", PipeKind.WATER.itemId());
        assertEquals("oil_pipe", PipeKind.OIL.itemId());
        assertEquals("lava_pipe", PipeKind.LAVA.itemId());
        assertEquals(FluidKind.OIL, PipeKind.OIL.fluid());
    }
}
