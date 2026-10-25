package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure tests that avoid method signatures referencing Minecraft types
 * (Player / ItemStack are not on the plain JUnit classpath).
 */
class BackpackRefTest {
    @Test
    void mainHandFactory() {
        BackpackRef ref = BackpackRef.mainHand();
        assertEquals(BackpackRef.Source.MAIN_HAND, ref.source());
        assertEquals(-1, ref.invIndex());
    }

    @Test
    void inventorySizeIsThirtySix() {
        // Compile-time constant — does not load Minecraft ItemStack types.
        assertEquals(36, BackpackInventory.SIZE);
        assertEquals(4, BackpackInventory.SIZE / 9);
    }

    @Test
    void sourceOrderMatchesSpec() {
        BackpackRef.Source[] order = BackpackRef.Source.values();
        assertEquals(BackpackRef.Source.MAIN_HAND, order[0]);
        assertEquals(BackpackRef.Source.OFF_HAND, order[1]);
        assertEquals(BackpackRef.Source.CHEST, order[2]);
        assertEquals(BackpackRef.Source.INVENTORY, order[3]);
    }
}
