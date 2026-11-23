package pnnh.mcfloatingbackpack.inventory.worn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 061: one floating backpack in each slot type of an accessory mod. */
class SlotLimitTest {
    @Test
    void anEmptyTypeTakesOne() {
        assertTrue(SlotLimit.allows(0, true));
    }

    @Test
    void aTypeWithABackpackAlreadyRefusesAnother() {
        assertFalse(SlotLimit.allows(1, true));
        assertFalse(SlotLimit.allows(2, true), "a type that got two by commands still refuses a third");
    }

    @Test
    void nothingIsLimitedWhenTheModIsNotAtFull() {
        assertTrue(SlotLimit.allows(0, false));
        assertTrue(SlotLimit.allows(1, false));
        assertTrue(SlotLimit.allows(5, false));
    }

    @Test
    void aNegativeCountIsTreatedAsNone() {
        assertTrue(SlotLimit.allows(-1, true));
    }
}
