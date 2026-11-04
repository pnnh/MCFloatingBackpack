package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelCursorRequest.Mode;

class PanelCursorRequestTest {
    private static int[] range(int from, int toInclusive) {
        int[] a = new int[toInclusive - from + 1];
        for (int i = 0; i < a.length; i++) {
            a[i] = from + i;
        }
        return a;
    }

    @Test
    void modeWireValuesAreStrict() {
        assertEquals(Mode.DISTRIBUTE_LEFT, Mode.fromByte((byte) 0));
        assertEquals(Mode.DISTRIBUTE_RIGHT, Mode.fromByte((byte) 1));
        assertEquals(Mode.GATHER_DRAG, Mode.fromByte((byte) 2));
        assertEquals(Mode.GATHER_ALL, Mode.fromByte((byte) 3));
        assertNull(Mode.fromByte((byte) 4));
        assertNull(Mode.fromByte((byte) -1));
        assertEquals(2, Mode.GATHER_DRAG.toByte());
    }

    @Test
    void unknownModeIsRejected() {
        assertFalse(PanelCursorRequest.validate(null, new int[] {0}, 54));
    }

    @Test
    void emptyNullOutOfRangeAndDuplicateListsAreRejected() {
        for (Mode mode : Mode.values()) {
            assertFalse(PanelCursorRequest.validate(mode, null, 54), mode.name());
            assertFalse(PanelCursorRequest.validate(mode, new int[0], 54), mode.name());
            assertFalse(PanelCursorRequest.validate(mode, new int[] {54}, 54), mode.name());
            assertFalse(PanelCursorRequest.validate(mode, new int[] {-1}, 54), mode.name());
        }
        assertFalse(PanelCursorRequest.validate(Mode.DISTRIBUTE_LEFT, new int[] {3, 3}, 54));
        assertFalse(PanelCursorRequest.validate(Mode.GATHER_DRAG, new int[] {1, 2, 1}, 54));
    }

    @Test
    void gatherAllNeedsExactlyOneSlot() {
        assertTrue(PanelCursorRequest.validate(Mode.GATHER_ALL, new int[] {5}, 54));
        assertFalse(PanelCursorRequest.validate(Mode.GATHER_ALL, new int[] {5, 6}, 54));
    }

    @Test
    void otherModesTakeOneUpToBackpackSizeSlots() {
        for (Mode mode : new Mode[] {Mode.DISTRIBUTE_LEFT, Mode.DISTRIBUTE_RIGHT, Mode.GATHER_DRAG}) {
            assertTrue(PanelCursorRequest.validate(mode, new int[] {0}, 54), mode.name());
            assertTrue(PanelCursorRequest.validate(mode, range(0, 53), 54), mode.name());
            assertFalse(PanelCursorRequest.validate(mode, range(0, 54), 54), mode.name());
        }
    }

    @Test
    void neverMoreThanTheLargestBackpack() {
        assertTrue(PanelCursorRequest.validate(Mode.DISTRIBUTE_LEFT, range(0, 107), 108));
        assertFalse(PanelCursorRequest.validate(Mode.DISTRIBUTE_LEFT, range(0, 108), 108));
        // a claimed size above the largest backpack does not raise the cap
        assertFalse(PanelCursorRequest.validate(Mode.DISTRIBUTE_LEFT, range(0, 108), 500));
    }
}
