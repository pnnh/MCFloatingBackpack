package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 022: which slots of the vanilla inventory and chest screens the delete key may take. */
class DeletableSlotsTest {
    @Test
    void ownMainInventoryIsNineToThirtyFive() {
        for (int slot = 9; slot <= 35; slot++) {
            assertTrue(DeletableSlots.allowed(true, slot, false), "slot " + slot);
        }
    }

    @Test
    void hotbarArmorAndOffhandAreNot() {
        for (int slot : new int[] {0, 1, 8, 36, 37, 38, 39, 40, -1, 41}) {
            assertFalse(DeletableSlots.allowed(true, slot, false), "slot " + slot);
        }
    }

    @Test
    void aChestsOwnSlotsAreAllowed() {
        assertTrue(DeletableSlots.allowed(false, 0, true));
        assertTrue(DeletableSlots.allowed(false, 53, true));
    }

    @Test
    void anythingElseIsNot() {
        assertFalse(DeletableSlots.allowed(false, 0, false));
        assertFalse(DeletableSlots.allowed(false, 20, false));
    }

    @Test
    void theOwnInventoryRuleWinsOverTheChestFlag() {
        assertFalse(DeletableSlots.allowed(true, 3, true));
        assertTrue(DeletableSlots.allowed(true, 20, true));
    }
}
