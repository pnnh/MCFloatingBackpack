package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.item.BackpackTier;

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
        assertEquals(36, BackpackTier.LEATHER.capacity());
        assertEquals(108, BackpackTier.NETHERITE.capacity());
        assertEquals(4, BackpackTier.LEATHER.rows());
    }

    @Test
    void sourceOrderMatchesSpec() {
        BackpackRef.Source[] order = BackpackRef.Source.values();
        assertEquals(BackpackRef.Source.MAIN_HAND, order[0]);
        assertEquals(BackpackRef.Source.OFF_HAND, order[1]);
        assertEquals(BackpackRef.Source.CHEST, order[2]);
        assertEquals(BackpackRef.Source.INVENTORY, order[3]);
    }

    @Test
    void onlyTheChestIsAWornSourceForNow() {
        // 032: a worn source is found through the worn-source list; a hand and the inventory are not worn
        assertEquals(true, BackpackRef.Source.CHEST.isWorn());
        assertEquals(false, BackpackRef.Source.MAIN_HAND.isWorn());
        assertEquals(false, BackpackRef.Source.OFF_HAND.isWorn());
        assertEquals(false, BackpackRef.Source.INVENTORY.isWorn());
    }

    @Test
    void sourceNumbersOnTheWireNeverChange() {
        // the numbers a reference travels with (protocol 16); a source added later goes after these
        assertEquals(0, BackpackRef.Source.MAIN_HAND.ordinal());
        assertEquals(1, BackpackRef.Source.OFF_HAND.ordinal());
        assertEquals(2, BackpackRef.Source.CHEST.ordinal());
        assertEquals(3, BackpackRef.Source.INVENTORY.ordinal());
    }

    @Test
    void aReferenceIntoAWornSourceIsAnOrdinaryRecord() {
        // the chest is slot 0 of its source (it was -1 before 032); both ends build it from the same code
        BackpackRef ref = new BackpackRef(BackpackRef.Source.CHEST, 0);
        assertEquals(BackpackRef.Source.CHEST, ref.source());
        assertEquals(0, ref.invIndex());
        assertEquals(new BackpackRef(BackpackRef.Source.CHEST, 0), ref);
    }

    @Test
    void curiosIsAWornSourceAddedAtTheEnd() {
        // 033: the sources already in use keep their numbers; Curios comes after them
        assertEquals(4, BackpackRef.Source.CURIOS.ordinal());
        assertEquals(true, BackpackRef.Source.CURIOS.isWorn());
        assertEquals(5, BackpackRef.Source.values().length);
    }
}
