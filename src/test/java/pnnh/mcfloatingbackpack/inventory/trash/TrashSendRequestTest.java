package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.trash.TrashSendRequest.Source;

/** 022: what the server accepts of a "send to the trash can" request before it touches any item. */
class TrashSendRequestTest {
    @Test
    void sourceBytesRoundTripAndUnknownIsNull() {
        for (Source source : Source.values()) {
            assertEquals(source, Source.fromByte(source.toByte()));
        }
        assertEquals(Source.DUMP, Source.fromByte((byte) 0));
        assertEquals(Source.MAIN_SLOTS, Source.fromByte((byte) 1));
        assertEquals(Source.MENU_SLOT, Source.fromByte((byte) 2));
        assertNull(Source.fromByte((byte) 3));
        assertNull(Source.fromByte((byte) -1));
    }

    @Test
    void dumpTakesNoSlots() {
        assertTrue(TrashSendRequest.validate(Source.DUMP, new int[0], 0, 27));
        assertFalse(TrashSendRequest.validate(Source.DUMP, new int[] {0}, 0, 27));
        assertFalse(TrashSendRequest.validate(Source.DUMP, null, 0, 27));
    }

    @Test
    void mainSlotsNeedDistinctIndicesInsideTheMainArea() {
        assertTrue(TrashSendRequest.validate(Source.MAIN_SLOTS, new int[] {0}, 0, 27));
        assertTrue(TrashSendRequest.validate(Source.MAIN_SLOTS, new int[] {0, 5, 26}, 0, 27));
        assertFalse(TrashSendRequest.validate(Source.MAIN_SLOTS, new int[0], 0, 27));
        assertFalse(TrashSendRequest.validate(Source.MAIN_SLOTS, new int[] {1, 1}, 0, 27));
        assertFalse(TrashSendRequest.validate(Source.MAIN_SLOTS, new int[] {27}, 0, 27));
        assertFalse(TrashSendRequest.validate(Source.MAIN_SLOTS, new int[] {-1}, 0, 27));
        assertFalse(TrashSendRequest.validate(Source.MAIN_SLOTS, null, 0, 27));
    }

    @Test
    void mainSlotsAreBoundedByTheSizeAndTheBiggestBackpack() {
        int[] tooMany = new int[28];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = i;
        }
        assertFalse(TrashSendRequest.validate(Source.MAIN_SLOTS, tooMany, 0, 27));
        int[] all = new int[423];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        assertTrue(TrashSendRequest.validate(Source.MAIN_SLOTS, all, 0, 423));
        int[] over = new int[424];
        for (int i = 0; i < over.length; i++) {
            over[i] = i;
        }
        assertFalse(TrashSendRequest.validate(Source.MAIN_SLOTS, over, 0, 500));
    }

    @Test
    void menuSlotNeedsExactlyOneSlotAndANonNegativeContainerId() {
        assertTrue(TrashSendRequest.validate(Source.MENU_SLOT, new int[] {0}, 0, 27));
        assertTrue(TrashSendRequest.validate(Source.MENU_SLOT, new int[] {255}, 7, 27));
        assertFalse(TrashSendRequest.validate(Source.MENU_SLOT, new int[] {256}, 7, 27));
        assertFalse(TrashSendRequest.validate(Source.MENU_SLOT, new int[] {-1}, 7, 27));
        assertFalse(TrashSendRequest.validate(Source.MENU_SLOT, new int[] {1, 2}, 7, 27));
        assertFalse(TrashSendRequest.validate(Source.MENU_SLOT, new int[0], 7, 27));
        assertFalse(TrashSendRequest.validate(Source.MENU_SLOT, new int[] {1}, -1, 27));
    }

    @Test
    void unknownSourceIsRejected() {
        assertFalse(TrashSendRequest.validate(null, new int[] {0}, 0, 27));
    }
}
