package pnnh.mcfloatingbackpack.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** 050: where a discharge takes its empty container from: the backpack's main cells first, then the inventory. */
class ContainerPickerTest {
    private static int pick(int main, int inventory, Integer... holders) {
        Set<Integer> has = Set.of(holders);
        return ContainerPicker.firstMatch(main, inventory, has::contains);
    }

    @Test
    void theBackpackIsUsedBeforeTheInventoryWhateverTheOrderOfPositions() {
        assertEquals(5, pick(36, 36, 5, 40));
        assertEquals(35, pick(36, 36, 35, 36));
        assertTrue(ContainerPicker.isInBackpack(35, 36));
    }

    @Test
    void withNothingInTheBackpackTheInventoryIsUsedHotbarFirst() {
        assertEquals(37, pick(36, 36, 37, 60));
        assertFalse(ContainerPicker.isInBackpack(37, 36));
        assertEquals(1, ContainerPicker.inventoryIndex(37, 36));
    }

    @Test
    void theFirstInEachPartWins() {
        assertEquals(3, pick(36, 36, 9, 3, 50));
    }

    @Test
    void nothingAnywhereIsMinusOne() {
        assertEquals(-1, pick(36, 36));
        assertEquals(-1, pick(0, 0, 3));
    }

    @Test
    void anEmptyBackpackMainAreaLeavesOnlyTheInventory() {
        assertEquals(0, pick(0, 36, 0));
        assertFalse(ContainerPicker.isInBackpack(0, 0));
    }

    @Test
    void positionsOutsideBothPartsAreNeverAsked() {
        int[] asked = {-1};
        ContainerPicker.firstMatch(3, 2, position -> {
            asked[0] = Math.max(asked[0], position);
            return false;
        });
        assertEquals(4, asked[0]);
    }
}
