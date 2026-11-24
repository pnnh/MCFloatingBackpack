package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 065: of several combined backpacks worn, the highest tier counts; of equal tiers the first in the search order. */
class CombinedWornTest {
    @Test
    void noneWornPicksNothing() {
        assertEquals(-1, CombinedWorn.pick(List.of()));
    }

    @Test
    void theHighestTierWinsWhereverItIs() {
        assertEquals(1, CombinedWorn.pick(List.of(0, 4, 2)));
        assertEquals(2, CombinedWorn.pick(List.of(0, 1, 3)));
    }

    @Test
    void equalTiersTakeTheFirstInSearchOrder() {
        assertEquals(0, CombinedWorn.pick(List.of(2, 2, 2)));
        assertEquals(1, CombinedWorn.pick(List.of(1, 3, 3)));
    }

    @Test
    void oneWornIsPicked() {
        assertEquals(0, CombinedWorn.pick(List.of(0)));
    }
}
