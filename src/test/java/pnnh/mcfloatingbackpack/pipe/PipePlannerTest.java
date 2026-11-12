package pnnh.mcfloatingbackpack.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Action;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Notice;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Plan;
import pnnh.mcfloatingbackpack.pipe.PipePlanner.Situation;
import pnnh.mcfloatingbackpack.tank.FluidKind;
import pnnh.mcfloatingbackpack.tank.FluidStore;
import pnnh.mcfloatingbackpack.tank.TankCatalog;

/** 049: what a pipe decides: the order of the refusals, the amounts, the notices, and that the store only changes when the action is done. */
class PipePlannerTest {
    private static final String MI = "modern_industrialization:crude_oil";
    private static final String PNC = "pneumaticcraft:oil";

    private static FluidStore store(FluidKind kind, int mb, String oilFluid) {
        return FluidStore.of(Map.of(kind, mb), oilFluid);
    }

    private static Situation with(BackpackTier tank, FluidStore store, String fluidId) {
        return new Situation(true, false, tank, store, fluidId);
    }

    // ---- the refusals both actions share ----

    @Test
    void noBackpackRefusesBoth() {
        Situation none = new Situation(false, false, null, FluidStore.EMPTY, "");
        for (Plan plan : new Plan[] {PipePlanner.absorb(PipeKind.WATER, none), PipePlanner.place(PipeKind.WATER, none)}) {
            assertEquals(Action.REFUSE, plan.action());
            assertEquals(Notice.NO_BACKPACK, plan.notice());
        }
    }

    @Test
    void aProtectedBackpackSaysSo() {
        Situation protectedOne = new Situation(false, true, null, FluidStore.EMPTY, "");
        assertEquals(Notice.BACKPACK_PROTECTED, PipePlanner.absorb(PipeKind.LAVA, protectedOne).notice());
        assertEquals(Notice.BACKPACK_PROTECTED, PipePlanner.place(PipeKind.LAVA, protectedOne).notice());
    }

    @Test
    void noTankRefusesBoth() {
        Situation noTank = with(null, store(FluidKind.WATER, 5000, ""), "");
        assertEquals(Notice.NO_TANK, PipePlanner.absorb(PipeKind.WATER, noTank).notice());
        assertEquals(Notice.NO_TANK, PipePlanner.place(PipeKind.WATER, noTank).notice());
        assertEquals(noTank.store(), PipePlanner.place(PipeKind.WATER, noTank).next());
    }

    // ---- absorbing ----

    @Test
    void absorbingAddsABucket() {
        Plan plan = PipePlanner.absorb(PipeKind.WATER, with(BackpackTier.IRON, FluidStore.EMPTY, ""));
        assertEquals(Action.DO, plan.action());
        assertEquals(1000, plan.next().amount(FluidKind.WATER));
        assertNull(plan.notice(), "not full yet");
    }

    @Test
    void theRoomMustHoldAWholeBucket() {
        // iron tank: 16000. 15001 leaves 999 of room, 15000 leaves exactly a bucket
        Plan tooLittle = PipePlanner.absorb(PipeKind.LAVA, with(BackpackTier.IRON, store(FluidKind.LAVA, 15001, ""), ""));
        assertEquals(Action.REFUSE, tooLittle.action());
        assertEquals(Notice.CANNOT_ABSORB_FULL, tooLittle.notice());
        assertEquals(15001, tooLittle.next().amount(FluidKind.LAVA));
        Plan exact = PipePlanner.absorb(PipeKind.LAVA, with(BackpackTier.IRON, store(FluidKind.LAVA, 15000, ""), ""));
        assertEquals(Action.DO, exact.action());
        assertEquals(16000, exact.next().amount(FluidKind.LAVA));
        assertEquals(Notice.TANK_FULL_NOW, exact.notice());
        Plan wide = PipePlanner.absorb(PipeKind.LAVA, with(BackpackTier.IRON, store(FluidKind.LAVA, 14999, ""), ""));
        assertEquals(Action.DO, wide.action());
        assertNull(wide.notice(), "999 mB are left: not full to the last drop, no notice");
    }

    @Test
    void aFullTankRefuses() {
        Plan plan = PipePlanner.absorb(PipeKind.WATER, with(BackpackTier.LEATHER, store(FluidKind.WATER, 8000, ""), ""));
        assertEquals(Action.REFUSE, plan.action());
        assertEquals(Notice.CANNOT_ABSORB_FULL, plan.notice());
    }

    @Test
    void theTierOnlyChangesTheCapacity() {
        for (BackpackTier tier : TankCatalog.tiers()) {
            int capacity = TankCatalog.capacityMb(tier);
            assertEquals(Action.DO, PipePlanner.absorb(PipeKind.WATER, with(tier, store(FluidKind.WATER, capacity - 1000, ""), "")).action(), tier.name());
            assertEquals(Action.REFUSE, PipePlanner.absorb(PipeKind.WATER, with(tier, store(FluidKind.WATER, capacity - 999, ""), "")).action(), tier.name());
        }
    }

    @Test
    void anAmountAboveTheCapacityFromOlderDataIsNotTouched() {
        Plan plan = PipePlanner.absorb(PipeKind.WATER, with(BackpackTier.LEATHER, store(FluidKind.WATER, 10000, ""), ""));
        assertEquals(Action.REFUSE, plan.action());
        assertEquals(10000, plan.next().amount(FluidKind.WATER));
    }

    // ---- crude oil ----

    @Test
    void theFirstOilLocksTheFluid() {
        Plan plan = PipePlanner.absorb(PipeKind.OIL, with(BackpackTier.IRON, FluidStore.EMPTY, PNC));
        assertEquals(Action.DO, plan.action());
        assertEquals(PNC, plan.next().lockedFluid());
        assertEquals(1000, plan.next().amount(FluidKind.OIL));
    }

    @Test
    void anotherOilIsRefusedWhileTheTankHoldsOne() {
        Situation locked = with(BackpackTier.IRON, store(FluidKind.OIL, 3000, MI), PNC);
        Plan plan = PipePlanner.absorb(PipeKind.OIL, locked);
        assertEquals(Action.REFUSE, plan.action());
        assertEquals(Notice.OIL_LOCKED, plan.notice());
        assertEquals(Action.DO, PipePlanner.absorb(PipeKind.OIL, with(BackpackTier.IRON, store(FluidKind.OIL, 3000, MI), MI)).action());
    }

    @Test
    void anEmptiedOilTankTakesAnyOilAgain() {
        Situation emptied = with(BackpackTier.IRON, FluidStore.of(Map.of(), ""), PNC);
        assertEquals(Action.DO, PipePlanner.absorb(PipeKind.OIL, emptied).action());
    }

    @Test
    void placingOilPlacesTheLockedFluid() {
        Plan plan = PipePlanner.place(PipeKind.OIL, with(BackpackTier.IRON, store(FluidKind.OIL, 2000, MI), ""));
        assertEquals(Action.DO, plan.action());
        assertEquals(MI, plan.fluidId());
        assertEquals(1000, plan.next().amount(FluidKind.OIL));
        assertEquals(MI, plan.next().lockedFluid());
    }

    @Test
    void placingTheLastOilUnlocks() {
        Plan plan = PipePlanner.place(PipeKind.OIL, with(BackpackTier.IRON, store(FluidKind.OIL, 1000, MI), ""));
        assertEquals(Notice.TANK_EMPTY_NOW, plan.notice());
        assertEquals("", plan.next().lockedFluid());
    }

    // ---- placing ----

    @Test
    void placingTakesABucketAndSaysWhenTheTankIsEmpty() {
        Plan more = PipePlanner.place(PipeKind.WATER, with(BackpackTier.IRON, store(FluidKind.WATER, 2000, ""), ""));
        assertEquals(Action.DO, more.action());
        assertEquals(1000, more.next().amount(FluidKind.WATER));
        assertNull(more.notice());
        Plan last = PipePlanner.place(PipeKind.WATER, with(BackpackTier.IRON, store(FluidKind.WATER, 1000, ""), ""));
        assertEquals(Action.DO, last.action());
        assertEquals(0, last.next().amount(FluidKind.WATER));
        assertEquals(Notice.TANK_EMPTY_NOW, last.notice());
    }

    @Test
    void lessThanABucketCannotBePlaced() {
        Plan plan = PipePlanner.place(PipeKind.LAVA, with(BackpackTier.IRON, store(FluidKind.LAVA, 999, ""), ""));
        assertEquals(Action.REFUSE, plan.action());
        assertEquals(Notice.CANNOT_PLACE_EMPTY, plan.notice());
        assertEquals(999, plan.next().amount(FluidKind.LAVA));
        assertEquals(Notice.CANNOT_PLACE_EMPTY, PipePlanner.place(PipeKind.LAVA, with(BackpackTier.IRON, FluidStore.EMPTY, "")).notice());
        assertEquals(Action.DO, PipePlanner.place(PipeKind.LAVA, with(BackpackTier.IRON, store(FluidKind.LAVA, 1001, ""), "")).action());
    }

    @Test
    void oilWithNothingLockedCannotBePlaced() {
        FluidStore odd = new FluidStore(Map.of(FluidKind.OIL, 3000), "");
        Plan plan = PipePlanner.place(PipeKind.OIL, with(BackpackTier.IRON, odd, ""));
        assertEquals(Action.REFUSE, plan.action());
        assertEquals(Notice.CANNOT_PLACE_EMPTY, plan.notice());
    }

    @Test
    void aPipeOnlyTouchesItsOwnLiquid() {
        FluidStore all = FluidStore.of(Map.of(FluidKind.WATER, 5000, FluidKind.MILK, 7000, FluidKind.LAVA, 2000, FluidKind.OIL, 1000), MI);
        FluidStore after = PipePlanner.place(PipeKind.WATER, with(BackpackTier.IRON, all, "")).next();
        assertEquals(4000, after.amount(FluidKind.WATER));
        assertEquals(7000, after.amount(FluidKind.MILK));
        assertEquals(2000, after.amount(FluidKind.LAVA));
        assertEquals(1000, after.amount(FluidKind.OIL));
        assertEquals(MI, after.lockedFluid());
    }

    // ---- the store only changes when the action is done ----

    @Test
    void randomActionsKeepTheLiquidInRangeAndOnlyChangeItWhenDone() {
        Random random = new Random(49);
        PipeKind[] pipes = PipeKind.values();
        BackpackTier[] tiers = TankCatalog.tiers().toArray(new BackpackTier[0]);
        String[] oils = {MI, PNC};
        for (int round = 0; round < 1000; round++) {
            BackpackTier tier = tiers[random.nextInt(tiers.length)];
            int capacity = TankCatalog.capacityMb(tier);
            FluidStore store = FluidStore.EMPTY;
            for (int step = 0; step < 40; step++) {
                PipeKind pipe = pipes[random.nextInt(pipes.length)];
                FluidKind kind = pipe.fluid();
                int before = store.amount(kind);
                String fluid = kind.locksFluid() ? oils[random.nextInt(oils.length)] : "";
                boolean absorb = random.nextBoolean();
                Plan plan = absorb
                        ? PipePlanner.absorb(pipe, with(tier, store, fluid))
                        : PipePlanner.place(pipe, with(tier, store, ""));
                if (plan.action() == Action.REFUSE) {
                    assertEquals(store, plan.next(), "a refusal changes nothing");
                    assertTrue(plan.notice() != null, "and says why");
                } else {
                    assertEquals(before + (absorb ? pipe.unitMb() : -pipe.unitMb()), plan.next().amount(kind));
                    assertTrue(plan.next().amount(kind) >= 0 && plan.next().amount(kind) <= capacity);
                    store = plan.next();
                }
                assertEquals(store.amount(FluidKind.OIL) > 0, !store.lockedFluid().isEmpty(), "the oil is locked exactly while there is some");
            }
        }
    }
    // ---- 050: milk, honey and filling a container ----

    private static final PipePlanner.Supply READY = new PipePlanner.Supply(true, true);

    private static Plan discharge(PipeKind pipe, BackpackTier tank, FluidStore store, PipePlanner.Supply supply) {
        return PipePlanner.discharge(pipe, with(tank, store, ""), supply);
    }

    @Test
    void theUnitIsABucketExceptForHoneyWhichIsABottle() {
        for (PipeKind pipe : PipeKind.values()) {
            assertEquals(pipe == PipeKind.HONEY ? 250 : 1000, pipe.unitMb(), pipe.name());
        }
        assertEquals(PipeKind.Container.GLASS_BOTTLE, PipeKind.HONEY.emptyContainer());
        assertEquals(PipeKind.Container.BUCKET, PipeKind.MILK.emptyContainer());
        assertTrue(PipeKind.WATER.hasBlockForm() && PipeKind.OIL.hasBlockForm() && PipeKind.LAVA.hasBlockForm());
        assertTrue(!PipeKind.MILK.hasBlockForm() && !PipeKind.HONEY.hasBlockForm());
    }

    @Test
    void honeyIsTakenInQuarterBuckets() {
        // iron tank 16000: 15750 leaves exactly a bottle, 15751 leaves 249
        Plan exact = PipePlanner.absorb(PipeKind.HONEY, with(BackpackTier.IRON, store(FluidKind.HONEY, 15750, ""), ""));
        assertEquals(Action.DO, exact.action());
        assertEquals(16000, exact.next().amount(FluidKind.HONEY));
        assertEquals(Notice.TANK_FULL_NOW, exact.notice());
        Plan tooLittle = PipePlanner.absorb(PipeKind.HONEY, with(BackpackTier.IRON, store(FluidKind.HONEY, 15751, ""), ""));
        assertEquals(Action.REFUSE, tooLittle.action());
        assertEquals(Notice.CANNOT_ABSORB_FULL, tooLittle.notice());
        Plan first = PipePlanner.absorb(PipeKind.HONEY, with(BackpackTier.LEATHER, FluidStore.EMPTY, ""));
        assertEquals(250, first.next().amount(FluidKind.HONEY));
        assertNull(first.notice());
    }

    @Test
    void milkIsTakenByTheBucket() {
        Plan plan = PipePlanner.absorb(PipeKind.MILK, with(BackpackTier.LEATHER, store(FluidKind.MILK, 6999, ""), ""));
        assertEquals(Action.DO, plan.action());
        assertEquals(7999, plan.next().amount(FluidKind.MILK));
        assertEquals(Notice.CANNOT_ABSORB_FULL, PipePlanner.absorb(PipeKind.MILK, with(BackpackTier.LEATHER, store(FluidKind.MILK, 7001, ""), "")).notice());
        assertEquals("", plan.next().lockedFluid());
    }

    @Test
    void aMilkOrHoneyPipeHasNothingToPlace() {
        for (PipeKind pipe : new PipeKind[] {PipeKind.MILK, PipeKind.HONEY}) {
            Situation none = new Situation(false, false, null, FluidStore.EMPTY, "");
            Plan plan = PipePlanner.place(pipe, none);
            assertEquals(Action.REFUSE, plan.action());
            assertEquals(Notice.NO_BLOCK_FORM, plan.notice(), "said before anything about the backpack");
            assertEquals(Notice.NO_BLOCK_FORM, PipePlanner.place(pipe, with(BackpackTier.IRON, store(pipe.fluid(), 5000, ""), "")).notice());
        }
    }

    @Test
    void dischargeTakesOneUnitAndSaysWhenTheTankIsEmpty() {
        Plan more = discharge(PipeKind.WATER, BackpackTier.IRON, store(FluidKind.WATER, 2000, ""), READY);
        assertEquals(Action.DO, more.action());
        assertEquals(1000, more.next().amount(FluidKind.WATER));
        assertNull(more.notice());
        Plan last = discharge(PipeKind.WATER, BackpackTier.IRON, store(FluidKind.WATER, 1000, ""), READY);
        assertEquals(0, last.next().amount(FluidKind.WATER));
        assertEquals(Notice.TANK_EMPTY_NOW, last.notice());
        Plan bottle = discharge(PipeKind.HONEY, BackpackTier.IRON, store(FluidKind.HONEY, 1000, ""), READY);
        assertEquals(750, bottle.next().amount(FluidKind.HONEY));
    }

    @Test
    void dischargeNeedsAWholeUnit() {
        for (int mb : new int[] {0, 999}) {
            Plan plan = discharge(PipeKind.LAVA, BackpackTier.IRON, store(FluidKind.LAVA, mb, ""), READY);
            assertEquals(Notice.CANNOT_PLACE_EMPTY, plan.notice(), "lava " + mb);
        }
        assertEquals(Action.DO, discharge(PipeKind.LAVA, BackpackTier.IRON, store(FluidKind.LAVA, 1000, ""), READY).action());
        assertEquals(Notice.CANNOT_PLACE_EMPTY, discharge(PipeKind.HONEY, BackpackTier.IRON, store(FluidKind.HONEY, 249, ""), READY).notice());
        assertEquals(Action.DO, discharge(PipeKind.HONEY, BackpackTier.IRON, store(FluidKind.HONEY, 250, ""), READY).action());
        assertEquals(Action.DO, discharge(PipeKind.HONEY, BackpackTier.IRON, store(FluidKind.HONEY, 251, ""), READY).action());
    }

    @Test
    void aMissingProductIsSaidBeforeAMissingContainer() {
        // an oil with no bucket: nothing is used up, and the player is not sent looking for an empty bucket for nothing
        PipePlanner.Supply neither = new PipePlanner.Supply(false, false);
        Plan plan = discharge(PipeKind.OIL, BackpackTier.IRON, store(FluidKind.OIL, 3000, MI), neither);
        assertEquals(Action.REFUSE, plan.action());
        assertEquals(Notice.NO_BUCKET_FOR_FLUID, plan.notice());
        assertEquals(3000, plan.next().amount(FluidKind.OIL));
    }

    @Test
    void noEmptyContainerRefusesAndChangesNothing() {
        FluidStore stored = store(FluidKind.WATER, 5000, "");
        Plan plan = discharge(PipeKind.WATER, BackpackTier.IRON, stored, new PipePlanner.Supply(false, true));
        assertEquals(Action.REFUSE, plan.action());
        assertEquals(Notice.NO_EMPTY_CONTAINER, plan.notice());
        assertEquals(stored, plan.next());
    }

    @Test
    void dischargeSharesTheBackpackAndTankRefusals() {
        assertEquals(Notice.NO_BACKPACK, PipePlanner.discharge(PipeKind.WATER, new Situation(false, false, null, FluidStore.EMPTY, ""), READY).notice());
        assertEquals(Notice.BACKPACK_PROTECTED, PipePlanner.discharge(PipeKind.WATER, new Situation(false, true, null, FluidStore.EMPTY, ""), READY).notice());
        assertEquals(Notice.NO_TANK, discharge(PipeKind.MILK, null, store(FluidKind.MILK, 5000, ""), READY).notice());
    }

    @Test
    void dischargingOilUsesTheLockedFluidAndTheLastBucketUnlocks() {
        Plan plan = discharge(PipeKind.OIL, BackpackTier.IRON, store(FluidKind.OIL, 1000, PNC), READY);
        assertEquals(PNC, plan.fluidId());
        assertEquals(Notice.TANK_EMPTY_NOW, plan.notice());
        assertEquals("", plan.next().lockedFluid());
        FluidStore odd = new FluidStore(Map.of(FluidKind.OIL, 3000), "");
        assertEquals(Notice.CANNOT_PLACE_EMPTY, discharge(PipeKind.OIL, BackpackTier.IRON, odd, READY).notice(), "oil of no known fluid has no bucket to make");
    }

    @Test
    void randomDischargesAndAbsorptionsKeepTheLiquidInRangeAndOnlyChangeItWhenDone() {
        Random random = new Random(50);
        PipeKind[] pipes = PipeKind.values();
        BackpackTier[] tiers = TankCatalog.tiers().toArray(new BackpackTier[0]);
        for (int round = 0; round < 1000; round++) {
            BackpackTier tier = tiers[random.nextInt(tiers.length)];
            int capacity = TankCatalog.capacityMb(tier);
            FluidStore store = FluidStore.EMPTY;
            for (int step = 0; step < 40; step++) {
                PipeKind pipe = pipes[random.nextInt(pipes.length)];
                FluidKind kind = pipe.fluid();
                int before = store.amount(kind);
                boolean absorb = random.nextBoolean();
                String fluid = kind.locksFluid() ? PNC : "";
                Plan plan = absorb
                        ? PipePlanner.absorb(pipe, with(tier, store, fluid))
                        : PipePlanner.discharge(pipe, with(tier, store, ""), new PipePlanner.Supply(random.nextInt(4) != 0, random.nextInt(10) != 0));
                if (plan.action() == Action.REFUSE) {
                    assertEquals(store, plan.next());
                } else {
                    assertEquals(before + (absorb ? pipe.unitMb() : -pipe.unitMb()), plan.next().amount(kind));
                    assertTrue(plan.next().amount(kind) >= 0 && plan.next().amount(kind) <= capacity);
                    store = plan.next();
                }
            }
        }
    }
}
