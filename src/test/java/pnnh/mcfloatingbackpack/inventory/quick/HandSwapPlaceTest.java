package pnnh.mcfloatingbackpack.inventory.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class HandSwapPlaceTest {
    @Test
    void anEmptyHandClearsTheClickedSlot() {
        assertEquals(4, HandSwapPlace.destination(4, true, false, List.of()));
    }

    @Test
    void theClickedSlotWinsWhenItAcceptsTheHandStack() {
        assertEquals(7, HandSwapPlace.destination(7, false, true, List.of(1, 2)));
    }

    @Test
    void otherwiseTheFirstOtherEmptySlotThatAcceptsIt() {
        assertEquals(2, HandSwapPlace.destination(7, false, false, List.of(7, 2, 5)));
    }

    @Test
    void nowhereToPutTheHandStackRefusesTheSwap() {
        assertEquals(-1, HandSwapPlace.destination(7, false, false, List.of(7)));
        assertEquals(-1, HandSwapPlace.destination(7, false, false, List.of()));
        assertEquals(-1, HandSwapPlace.destination(7, false, false, null));
        assertEquals(-1, HandSwapPlace.destination(-1, false, true, List.of(0)));
    }
}
