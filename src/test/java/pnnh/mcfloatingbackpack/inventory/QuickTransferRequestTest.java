package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest.Direction;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest.Holder;

class QuickTransferRequestTest {
    private static int[] range(int from, int toInclusive) {
        int[] a = new int[toInclusive - from + 1];
        for (int i = 0; i < a.length; i++) {
            a[i] = from + i;
        }
        return a;
    }

    @Test
    void toBackpackAcceptsInventoryIndices() {
        assertTrue(QuickTransferRequest.validate(Direction.TO_BACKPACK, new int[] {0}, 36));
        assertTrue(QuickTransferRequest.validate(Direction.TO_BACKPACK, new int[] {35, 0, 9}, 36));
        assertTrue(QuickTransferRequest.validate(Direction.TO_BACKPACK, range(0, 35), 36));
    }

    @Test
    void toBackpackRejectsOutOfRangeAndOverLimit() {
        assertFalse(QuickTransferRequest.validate(Direction.TO_BACKPACK, new int[] {36}, 108));
        assertFalse(QuickTransferRequest.validate(Direction.TO_BACKPACK, new int[] {-1}, 108));
        assertFalse(QuickTransferRequest.validate(Direction.TO_BACKPACK, new int[37], 108));
    }

    @Test
    void toInventoryIsBoundedByBackpackSize() {
        assertTrue(QuickTransferRequest.validate(Direction.TO_INVENTORY, new int[] {0, 53}, 54));
        assertFalse(QuickTransferRequest.validate(Direction.TO_INVENTORY, new int[] {54}, 54));
        assertTrue(QuickTransferRequest.validate(Direction.TO_INVENTORY, range(0, 107), 108));
        assertFalse(QuickTransferRequest.validate(Direction.TO_INVENTORY, range(0, 108), 108));
    }

    @Test
    void emptyDuplicateOrNullListsAreRejected() {
        assertFalse(QuickTransferRequest.validate(Direction.TO_BACKPACK, new int[0], 36));
        assertFalse(QuickTransferRequest.validate(Direction.TO_BACKPACK, null, 36));
        assertFalse(QuickTransferRequest.validate(Direction.TO_BACKPACK, new int[] {3, 4, 3}, 36));
        assertFalse(QuickTransferRequest.validate(Direction.TO_INVENTORY, new int[] {7, 7}, 54));
    }

    @Test
    void protectedIndexFollowsWhereTheBackpackLives() {
        assertEquals(4, QuickTransferRequest.protectedInventoryIndex(Holder.MAIN_HAND, -1, 4));
        assertEquals(17, QuickTransferRequest.protectedInventoryIndex(Holder.INVENTORY, 17, 4));
        assertEquals(-1, QuickTransferRequest.protectedInventoryIndex(Holder.OFF_HAND, -1, 4));
        assertEquals(-1, QuickTransferRequest.protectedInventoryIndex(Holder.CHEST, -1, 4));
    }

    @Test
    void directionFromByteIsStrict() {
        assertEquals(Direction.TO_BACKPACK, Direction.fromByte((byte) 0));
        assertEquals(Direction.TO_INVENTORY, Direction.fromByte((byte) 1));
        assertEquals(null, Direction.fromByte((byte) 2));
        assertEquals(null, Direction.fromByte((byte) -1));
    }
}
