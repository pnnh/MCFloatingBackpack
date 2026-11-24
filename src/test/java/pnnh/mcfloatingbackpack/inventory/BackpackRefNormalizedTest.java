package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.BackpackRef.Source;

/** 065: the main hand and the selected inventory slot are one backpack. */
class BackpackRefNormalizedTest {
    @Test
    void theMainHandBecomesTheSelectedInventorySlot() {
        assertEquals(new BackpackRef(Source.INVENTORY, 4), BackpackRef.mainHand().normalized(4));
    }

    @Test
    void everythingElseStaysAsItIs() {
        for (BackpackRef ref : new BackpackRef[] {
            new BackpackRef(Source.INVENTORY, 7), new BackpackRef(Source.OFF_HAND, -1), new BackpackRef(Source.CHEST, 0), new BackpackRef(Source.CURIOS_DEDICATED, 0)
        }) {
            assertEquals(ref, ref.normalized(3));
        }
    }
}
