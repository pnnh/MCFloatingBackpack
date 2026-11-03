package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure stand-in for ItemStack click rules (no Minecraft bootstrap required).
 * Production code in {@link BackpackClickLogic} mirrors this behavior with ItemStack.
 */
class BackpackClickLogicTest {
    private FakeStack[] slots;
    private FakeStack cursor;

    @BeforeEach
    void setUp() {
        slots = new FakeStack[3];
        Arrays.fill(slots, FakeStack.EMPTY);
        cursor = FakeStack.EMPTY;
    }

    @Test
    void pickFromSlotToEmptyCursor() {
        slots[0] = new FakeStack(1, 3, 64);
        cursor = leftClick(0);
        assertTrue(slots[0].isEmpty());
        assertEquals(new FakeStack(1, 3, 64), cursor);
    }

    @Test
    void placeCursorIntoEmptySlot() {
        cursor = new FakeStack(2, 2, 64);
        cursor = leftClick(1);
        assertTrue(cursor.isEmpty());
        assertEquals(new FakeStack(2, 2, 64), slots[1]);
    }

    @Test
    void mergeSameItem() {
        slots[0] = new FakeStack(3, 60, 64);
        cursor = new FakeStack(3, 10, 64);
        cursor = leftClick(0);
        assertEquals(64, slots[0].count);
        assertEquals(6, cursor.count);
    }

    @Test
    void swapDifferentItems() {
        slots[2] = new FakeStack(4, 1, 64);
        cursor = new FakeStack(5, 1, 64);
        cursor = leftClick(2);
        assertEquals(5, slots[2].id);
        assertEquals(4, cursor.id);
    }

    @Test
    void rightClickPicksUpCeilingHalfLeavingFloorHalf() {
        slots[0] = new FakeStack(1, 5, 64);
        cursor = rightClick(0);
        assertEquals(2, slots[0].count);
        assertEquals(3, cursor.count);
    }

    @Test
    void rightClickPlacesSingleItemIntoEmptySlot() {
        cursor = new FakeStack(2, 5, 64);
        cursor = rightClick(1);
        assertEquals(1, slots[1].count);
        assertEquals(4, cursor.count);
    }

    @Test
    void rightClickMergeAddsOnlyOneItem() {
        slots[0] = new FakeStack(3, 60, 64);
        cursor = new FakeStack(3, 10, 64);
        cursor = rightClick(0);
        assertEquals(61, slots[0].count);
        assertEquals(9, cursor.count);
    }

    @Test
    void rightClickOnFullMatchingSlotIsNoOp() {
        slots[0] = new FakeStack(3, 64, 64);
        cursor = new FakeStack(3, 10, 64);
        cursor = rightClick(0);
        assertEquals(64, slots[0].count);
        assertEquals(10, cursor.count);
    }

    @Test
    void rightClickSwapsDifferentItemsFully() {
        slots[2] = new FakeStack(4, 1, 64);
        cursor = new FakeStack(5, 1, 64);
        cursor = rightClick(2);
        assertEquals(5, slots[2].id);
        assertEquals(4, cursor.id);
    }

    private FakeStack leftClick(int slotIndex) {
        return click(slotIndex, false);
    }

    private FakeStack rightClick(int slotIndex) {
        return click(slotIndex, true);
    }

    private FakeStack click(int slotIndex, boolean rightClick) {
        FakeStack slotStack = slots[slotIndex];
        FakeStack held = cursor;

        if (held.isEmpty()) {
            if (slotStack.isEmpty()) {
                return FakeStack.EMPTY;
            }
            if (!rightClick) {
                slots[slotIndex] = FakeStack.EMPTY;
                return slotStack;
            }
            int pickup = (slotStack.count + 1) / 2;
            int remain = slotStack.count - pickup;
            slots[slotIndex] = remain <= 0 ? FakeStack.EMPTY : new FakeStack(slotStack.id, remain, slotStack.max);
            return new FakeStack(slotStack.id, pickup, slotStack.max);
        }

        if (slotStack.isEmpty()) {
            if (!rightClick) {
                slots[slotIndex] = held;
                return FakeStack.EMPTY;
            }
            slots[slotIndex] = new FakeStack(held.id, 1, held.max);
            int remain = held.count - 1;
            return remain <= 0 ? FakeStack.EMPTY : new FakeStack(held.id, remain, held.max);
        }

        if (held.id == slotStack.id) {
            int space = slotStack.max - slotStack.count;
            if (space <= 0) {
                return held;
            }
            int move = Math.min(rightClick ? 1 : space, Math.min(space, held.count));
            slots[slotIndex] = new FakeStack(slotStack.id, slotStack.count + move, slotStack.max);
            int remain = held.count - move;
            return remain <= 0 ? FakeStack.EMPTY : new FakeStack(held.id, remain, held.max);
        }

        slots[slotIndex] = held;
        return slotStack;
    }

    private record FakeStack(int id, int count, int max) {
        static final FakeStack EMPTY = new FakeStack(0, 0, 64);

        boolean isEmpty() {
            return count <= 0 || id == 0;
        }
    }
}
