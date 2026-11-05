package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.trash.TrashRequest.Action;

/** 021: what the server accepts from a trash can request before it touches any item. */
class TrashRequestTest {
    @Test
    void actionBytesRoundTripAndUnknownIsNull() {
        for (Action action : Action.values()) {
            assertEquals(action, Action.fromByte(action.toByte()));
        }
        assertEquals(Action.ADD, Action.fromByte((byte) 0));
        assertEquals(Action.TAKE, Action.fromByte((byte) 1));
        assertEquals(Action.RESTORE, Action.fromByte((byte) 2));
        assertNull(Action.fromByte((byte) 3));
        assertNull(Action.fromByte((byte) -1));
    }

    @Test
    void addTakesNoSlots() {
        assertTrue(TrashRequest.validate(Action.ADD, new int[0]));
        assertFalse(TrashRequest.validate(Action.ADD, new int[] {0}));
        assertFalse(TrashRequest.validate(Action.ADD, null));
    }

    @Test
    void takeNeedsExactlyOneSlotInRange() {
        assertTrue(TrashRequest.validate(Action.TAKE, new int[] {0}));
        assertTrue(TrashRequest.validate(Action.TAKE, new int[] {8}));
        assertFalse(TrashRequest.validate(Action.TAKE, new int[0]));
        assertFalse(TrashRequest.validate(Action.TAKE, new int[] {1, 2}));
        assertFalse(TrashRequest.validate(Action.TAKE, new int[] {9}));
        assertFalse(TrashRequest.validate(Action.TAKE, new int[] {-1}));
        assertFalse(TrashRequest.validate(Action.TAKE, null));
    }

    @Test
    void restoreNeedsDistinctSlotsInRange() {
        assertTrue(TrashRequest.validate(Action.RESTORE, new int[] {3}));
        assertTrue(TrashRequest.validate(Action.RESTORE, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8}));
        assertFalse(TrashRequest.validate(Action.RESTORE, new int[0]));
        assertFalse(TrashRequest.validate(Action.RESTORE, new int[] {1, 1}));
        assertFalse(TrashRequest.validate(Action.RESTORE, new int[] {0, 9}));
        assertFalse(TrashRequest.validate(Action.RESTORE, new int[] {-2}));
        assertFalse(TrashRequest.validate(Action.RESTORE, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 0}));
        assertFalse(TrashRequest.validate(Action.RESTORE, null));
    }

    @Test
    void unknownActionIsRejected() {
        assertFalse(TrashRequest.validate(null, new int[] {0}));
    }
}
