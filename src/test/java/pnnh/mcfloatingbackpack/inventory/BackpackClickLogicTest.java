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

    private FakeStack leftClick(int slotIndex) {
        FakeStack slotStack = slots[slotIndex];
        FakeStack held = cursor;

        if (held.isEmpty()) {
            if (!slotStack.isEmpty()) {
                slots[slotIndex] = FakeStack.EMPTY;
                return slotStack;
            }
            return FakeStack.EMPTY;
        }

        if (slotStack.isEmpty()) {
            slots[slotIndex] = held;
            return FakeStack.EMPTY;
        }

        if (held.id == slotStack.id) {
            int space = slotStack.max - slotStack.count;
            if (space <= 0) {
                return held;
            }
            int move = Math.min(space, held.count);
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
