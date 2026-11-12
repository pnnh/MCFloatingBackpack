package pnnh.mcfloatingbackpack.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.tank.FluidStoreOps.Deposit;
import pnnh.mcfloatingbackpack.tank.FluidStoreOps.Drain;
import pnnh.mcfloatingbackpack.tank.FluidStoreOps.Reason;

/** 048: the liquid record and its two operations: capacity, the oil staying one fluid, nothing lost or made up. */
class FluidStoreTest {
    private static final String MI = "modern_industrialization:crude_oil";
    private static final String PNC = "pneumaticcraft:oil";

    @Test
    void anEmptyStoreHoldsNothing() {
        assertTrue(FluidStore.EMPTY.isEmpty());
        for (FluidKind kind : FluidKind.values()) {
            assertEquals(0, FluidStore.EMPTY.amount(kind));
        }
        assertEquals("", FluidStore.EMPTY.lockedFluid());
    }

    @Test
    void readingCleansTheStore() {
        FluidStore store = FluidStore.of(Map.of(FluidKind.WATER, -5, FluidKind.LAVA, 0, FluidKind.MILK, 300), MI);
        assertEquals(0, store.amount(FluidKind.WATER));
        assertEquals(0, store.amount(FluidKind.LAVA));
        assertEquals(300, store.amount(FluidKind.MILK));
        assertEquals("", store.lockedFluid(), "a lock without oil is dropped");
        assertEquals(MI, FluidStore.of(Map.of(FluidKind.OIL, 5), MI).lockedFluid());
    }

    @Test
    void depositFillsUpToTheCapacity() {
        Deposit first = FluidStoreOps.deposit(FluidStore.EMPTY, FluidKind.WATER, "", 5_000, 8_000);
        assertEquals(5_000, first.accepted());
        assertEquals(Reason.OK, first.reason());
        Deposit second = FluidStoreOps.deposit(first.store(), FluidKind.WATER, "", 5_000, 8_000);
        assertEquals(3_000, second.accepted());
        assertEquals(Reason.FULL, second.reason());
        assertEquals(8_000, second.store().amount(FluidKind.WATER));
        Deposit third = FluidStoreOps.deposit(second.store(), FluidKind.WATER, "", 1, 8_000);
        assertEquals(0, third.accepted());
        assertEquals(Reason.FULL, third.reason());
        assertEquals(second.store(), third.store());
    }

    @Test
    void depositToExactlyFullIsOk() {
        Deposit deposit = FluidStoreOps.deposit(FluidStore.EMPTY, FluidKind.HONEY, "", 8_000, 8_000);
        assertEquals(8_000, deposit.accepted());
        assertEquals(Reason.OK, deposit.reason());
    }

    @Test
    void anAmountAboveTheCapacityFromOlderDataIsKeptNotTrimmed() {
        FluidStore store = FluidStore.of(Map.of(FluidKind.WATER, 10_000), "");
        Deposit deposit = FluidStoreOps.deposit(store, FluidKind.WATER, "", 1_000, 8_000);
        assertEquals(0, deposit.accepted());
        assertEquals(10_000, deposit.store().amount(FluidKind.WATER));
        Drain drain = FluidStoreOps.drain(store, FluidKind.WATER, 1_000);
        assertEquals(9_000, drain.store().amount(FluidKind.WATER));
    }

    @Test
    void theFirstOilLocksTheFluidUntilItIsEmpty() {
        Deposit first = FluidStoreOps.deposit(FluidStore.EMPTY, FluidKind.OIL, MI, 1_000, 16_000);
        assertEquals(MI, first.store().lockedFluid());
        Deposit other = FluidStoreOps.deposit(first.store(), FluidKind.OIL, PNC, 1_000, 16_000);
        assertEquals(0, other.accepted());
        assertEquals(Reason.LOCKED_OTHER, other.reason());
        assertFalse(FluidStoreOps.canAccept(first.store(), FluidKind.OIL, PNC));
        assertTrue(FluidStoreOps.canAccept(first.store(), FluidKind.OIL, MI));
        Deposit same = FluidStoreOps.deposit(first.store(), FluidKind.OIL, MI, 1_000, 16_000);
        assertEquals(2_000, same.store().amount(FluidKind.OIL));

        Drain part = FluidStoreOps.drain(same.store(), FluidKind.OIL, 1_500);
        assertEquals(MI, part.fluidId());
        assertEquals(MI, part.store().lockedFluid(), "still oil, still locked");
        Drain rest = FluidStoreOps.drain(part.store(), FluidKind.OIL, 1_000);
        assertEquals(500, rest.removed());
        assertEquals(Reason.EMPTY, rest.reason());
        assertEquals("", rest.store().lockedFluid(), "empty unlocks");
        assertTrue(FluidStoreOps.canAccept(rest.store(), FluidKind.OIL, PNC));
        assertEquals(PNC, FluidStoreOps.deposit(rest.store(), FluidKind.OIL, PNC, 1_000, 16_000).store().lockedFluid());
    }

    @Test
    void otherLiquidsNeverLock() {
        Deposit deposit = FluidStoreOps.deposit(FluidStore.EMPTY, FluidKind.WATER, "anything", 1_000, 8_000);
        assertEquals("", deposit.store().lockedFluid());
        assertTrue(FluidStoreOps.canAccept(deposit.store(), FluidKind.WATER, "something else"));
    }

    @Test
    void drainTakesWhatThereIsAndReportsEmpty() {
        FluidStore store = FluidStore.of(Map.of(FluidKind.LAVA, 1_500), "");
        Drain one = FluidStoreOps.drain(store, FluidKind.LAVA, 1_000);
        assertEquals(1_000, one.removed());
        assertEquals(Reason.OK, one.reason());
        Drain two = FluidStoreOps.drain(one.store(), FluidKind.LAVA, 1_000);
        assertEquals(500, two.removed());
        assertEquals(Reason.EMPTY, two.reason());
        assertTrue(two.store().isEmpty());
        Drain three = FluidStoreOps.drain(two.store(), FluidKind.LAVA, 1_000);
        assertEquals(0, three.removed());
        assertEquals(Reason.EMPTY, three.reason());
    }

    @Test
    void zeroOrNegativeRequestsChangeNothing() {
        FluidStore store = FluidStore.of(Map.of(FluidKind.WATER, 100), "");
        assertEquals(store, FluidStoreOps.deposit(store, FluidKind.WATER, "", 0, 8_000).store());
        assertEquals(store, FluidStoreOps.drain(store, FluidKind.WATER, -3).store());
    }

    @Test
    void otherLiquidsAreNotTouchedByAnOperation() {
        FluidStore store = FluidStore.of(Map.of(FluidKind.WATER, 100, FluidKind.MILK, 200, FluidKind.OIL, 300), MI);
        FluidStore after = FluidStoreOps.deposit(store, FluidKind.WATER, "", 50, 8_000).store();
        assertEquals(150, after.amount(FluidKind.WATER));
        assertEquals(200, after.amount(FluidKind.MILK));
        assertEquals(300, after.amount(FluidKind.OIL));
        assertEquals(MI, after.lockedFluid());
    }

    @Test
    void randomOperationsConserveLiquidAndKeepTheInvariants() {
        Random random = new Random(2048);
        FluidKind[] kinds = FluidKind.values();
        String[] oils = {MI, PNC, "immersive_petroleum:crude_oil"};
        for (int round = 0; round < 1000; round++) {
            FluidStore store = FluidStore.EMPTY;
            for (int step = 0; step < 60; step++) {
                FluidKind kind = kinds[random.nextInt(kinds.length)];
                int before = store.amount(kind);
                int mb = random.nextInt(4_000);
                if (random.nextBoolean()) {
                    String fluid = kind.locksFluid() ? oils[random.nextInt(oils.length)] : "";
                    Deposit deposit = FluidStoreOps.deposit(store, kind, fluid, mb, 8_000);
                    assertEquals(before + deposit.accepted(), deposit.store().amount(kind));
                    assertTrue(deposit.store().amount(kind) <= Math.max(before, 8_000));
                    store = deposit.store();
                } else {
                    Drain drain = FluidStoreOps.drain(store, kind, mb);
                    assertEquals(before - drain.removed(), drain.store().amount(kind));
                    assertTrue(drain.removed() <= mb);
                    store = drain.store();
                }
                boolean hasOil = store.amount(FluidKind.OIL) > 0;
                assertEquals(hasOil, !store.lockedFluid().isEmpty(), "the lock exists exactly while there is oil");
            }
        }
    }
}
