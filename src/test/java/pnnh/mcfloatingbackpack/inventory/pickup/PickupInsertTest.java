package pnnh.mcfloatingbackpack.inventory.pickup;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

/** 024: putting a picked-up stack into the main area, over a fake stack type. */
class PickupInsertTest {
    record Fake(int kind, int count, int max) {
        static final Fake EMPTY = new Fake(0, 0, 64);
    }

    static final QuickTransferLogic.StackOps<Fake> OPS = new QuickTransferLogic.StackOps<>() {
        @Override
        public Fake empty() {
            return Fake.EMPTY;
        }

        @Override
        public boolean isEmpty(Fake s) {
            return s.count() <= 0;
        }

        @Override
        public int count(Fake s) {
            return s.count();
        }

        @Override
        public int maxStack(Fake s) {
            return s.max();
        }

        @Override
        public boolean sameKind(Fake a, Fake b) {
            return a.kind() == b.kind();
        }

        @Override
        public Fake withCount(Fake s, int count) {
            return count <= 0 ? Fake.EMPTY : new Fake(s.kind(), count, s.max());
        }
    };

    private static Fake f(int kind, int count) {
        return new Fake(kind, count, 64);
    }

    private static Fake[] slots(int size) {
        Fake[] slots = new Fake[size];
        Arrays.fill(slots, Fake.EMPTY);
        return slots;
    }

    private static PickupInsert.Result<Fake> run(Fake incoming, Fake[] slots) {
        return PickupInsert.insert(OPS, incoming, slots.length, i -> slots[i], (stack, i) -> slots[i] = stack);
    }

    private static int total(Fake[] slots) {
        return Arrays.stream(slots).mapToInt(Fake::count).sum();
    }

    @Test
    void everythingFitsStackingFirstThenUsingEmptySlots() {
        Fake[] slots = slots(3);
        slots[0] = f(2, 10);
        slots[1] = f(1, 60);
        PickupInsert.Result<Fake> result = run(f(1, 10), slots);
        assertTrue(OPS.isEmpty(result.rest()));
        assertEquals(10, result.moved());
        assertEquals(f(1, 64), slots[1]);
        assertEquals(f(1, 6), slots[2]);
        assertEquals(f(2, 10), slots[0]);
        assertArrayEquals(new int[] {1, 2}, result.changedSlots());
    }

    @Test
    void onlyPartFitsAndTheRestIsReturned() {
        Fake[] slots = slots(2);
        slots[0] = f(2, 1);
        slots[1] = f(1, 44);
        PickupInsert.Result<Fake> result = run(f(1, 64), slots);
        assertEquals(20, result.moved());
        assertEquals(f(1, 44), result.rest());
        assertEquals(f(1, 64), slots[1]);
        assertArrayEquals(new int[] {1}, result.changedSlots());
    }

    @Test
    void nothingFitsLeavesTheSlotsAlone() {
        Fake[] slots = {f(2, 64), f(3, 64)};
        PickupInsert.Result<Fake> result = run(f(1, 5), slots);
        assertEquals(0, result.moved());
        assertEquals(f(1, 5), result.rest());
        assertEquals(0, result.changedSlots().length);
        assertEquals(f(2, 64), slots[0]);
        assertEquals(f(3, 64), slots[1]);
    }

    @Test
    void emptyIncomingMovesNothing() {
        Fake[] slots = slots(2);
        PickupInsert.Result<Fake> result = run(Fake.EMPTY, slots);
        assertEquals(0, result.moved());
        assertEquals(0, result.changedSlots().length);
    }

    @Test
    void changedSlotsAreAscendingWithoutDuplicates() {
        Fake[] slots = slots(5);
        slots[3] = f(1, 60);
        slots[1] = f(1, 62);
        PickupInsert.Result<Fake> result = run(f(1, 20), slots);
        int[] changed = result.changedSlots();
        for (int i = 1; i < changed.length; i++) {
            assertTrue(changed[i] > changed[i - 1]);
        }
    }

    @Test
    void movedPlusRestAlwaysEqualsTheIncomingCountAndNothingIsLostOrCreated() {
        Random random = new Random(24);
        for (int round = 0; round < 200; round++) {
            Fake[] slots = slots(1 + random.nextInt(6));
            for (int i = 0; i < slots.length; i++) {
                if (random.nextInt(3) > 0) {
                    slots[i] = f(1 + random.nextInt(2), 1 + random.nextInt(64));
                }
            }
            Fake incoming = f(1 + random.nextInt(2), 1 + random.nextInt(64));
            int before = total(slots);
            PickupInsert.Result<Fake> result = run(incoming, slots);
            assertEquals(incoming.count(), result.moved() + result.rest().count());
            assertEquals(before + result.moved(), total(slots));
            for (Fake slot : slots) {
                assertTrue(slot.count() <= slot.max());
            }
        }
    }
}
