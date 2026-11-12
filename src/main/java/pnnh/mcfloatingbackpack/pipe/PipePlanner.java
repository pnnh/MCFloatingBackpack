package pnnh.mcfloatingbackpack.pipe;

import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.tank.FluidKind;
import pnnh.mcfloatingbackpack.tank.FluidStore;
import pnnh.mcfloatingbackpack.tank.FluidStoreOps;
import pnnh.mcfloatingbackpack.tank.TankCatalog;

/**
 * 049/050: what a pipe does and says, decided before anything in the world is touched. The caller then changes the world and only if that worked
 * keeps {@link Plan#next()} (so the world and the backpack's liquid change together or not at all). One action moves one unit of the liquid:
 * a bucket, or a bottle for honey ({@link PipeKind#unitMb()}). Pure logic, no Minecraft types.
 */
public final class PipePlanner {
    /** One bucket (1000 mB): what an action moves for every liquid but honey, which moves a bottle ({@link PipeKind#unitMb()}). */
    public static final int BUCKET_MB = 1000;

    /** What the pipe says to the player. */
    public enum Notice {
        NO_BACKPACK,
        BACKPACK_PROTECTED,
        NO_TANK,
        OIL_LOCKED,
        CANNOT_ABSORB_FULL,
        CANNOT_PLACE_EMPTY,
        TANK_FULL_NOW,
        TANK_EMPTY_NOW,
        /** 050: a discharge with no empty bucket (or glass bottle for honey) in the backpack or the inventory. */
        NO_EMPTY_CONTAINER,
        /** 050: a discharge of an oil whose fluid has no bucket item. */
        NO_BUCKET_FOR_FLUID,
        /** 050: a milk or honey pipe that is asked to pour out a block. */
        NO_BLOCK_FORM,
        /** Never decided here: the oil in the tank is a fluid that is not registered any more (its mod is gone); the world layer says so. */
        NO_FLUID
    }

    public enum Action { DO, REFUSE }

    /**
     * The state of the backpack the pipe would work with.
     *
     * @param backpackFound     the backpack the B key would open was found
     * @param backpackProtected it was found but may not be used (a newer version saved it, 040)
     * @param tankTier          the tier of the tank in the bottom area for the pipe's liquid, {@code null} if there is none
     * @param store             what the backpack holds
     * @param fluidId           the fluid the pipe is about to take in (absorbing; the world's fluid); unused when placing
     */
    public record Situation(boolean backpackFound, boolean backpackProtected, BackpackTier tankTier, FluidStore store, String fluidId) {
    }

    /**
     * The decision: {@code next} is the store to keep when the world change worked (the same store when refused); {@code fluidId} is the fluid
     * to place (placing); {@code notice} is what to tell the player, {@code null} for nothing.
     */
    public record Plan(Action action, FluidStore next, String fluidId, Notice notice) {
    }

    /** 050: what a discharge can use: an empty container to fill, and the filled container the liquid makes (an oil may have no bucket). */
    public record Supply(boolean hasEmptyContainer, boolean productExists) {
    }

    private PipePlanner() {
    }

    public static Plan absorb(PipeKind pipe, Situation situation) {
        Plan common = commonRefusal(situation);
        if (common != null) {
            return common;
        }
        FluidKind kind = pipe.fluid();
        FluidStore store = situation.store();
        if (!FluidStoreOps.canAccept(store, kind, situation.fluidId())) {
            return refuse(store, Notice.OIL_LOCKED);
        }
        int capacity = TankCatalog.capacityMb(situation.tankTier());
        int unit = pipe.unitMb();
        if (capacity - store.amount(kind) < unit) {
            return refuse(store, Notice.CANNOT_ABSORB_FULL);
        }
        FluidStoreOps.Deposit deposit = FluidStoreOps.deposit(store, kind, situation.fluidId(), unit, capacity);
        boolean full = deposit.store().amount(kind) >= capacity;
        return new Plan(Action.DO, deposit.store(), situation.fluidId(), full ? Notice.TANK_FULL_NOW : null);
    }

    public static Plan place(PipeKind pipe, Situation situation) {
        if (!pipe.hasBlockForm()) {
            return refuse(situation.store(), Notice.NO_BLOCK_FORM);
        }
        Plan common = commonRefusal(situation);
        if (common != null) {
            return common;
        }
        FluidKind kind = pipe.fluid();
        FluidStore store = situation.store();
        // oil is placed as the fluid it is locked to; with none locked there is nothing to place
        if (store.amount(kind) < pipe.unitMb() || (kind.locksFluid() && store.lockedFluid().isEmpty())) {
            return refuse(store, Notice.CANNOT_PLACE_EMPTY);
        }
        String fluid = kind.locksFluid() ? store.lockedFluid() : "";
        FluidStoreOps.Drain drain = FluidStoreOps.drain(store, kind, pipe.unitMb());
        boolean empty = drain.store().amount(kind) <= 0;
        return new Plan(Action.DO, drain.store(), fluid, empty ? Notice.TANK_EMPTY_NOW : null);
    }

    /**
     * 050: fills an empty container from the tank. The liquid must be at least one unit; then the filled container must exist (an oil may have no
     * bucket), and only then is the lack of an empty container the reason. {@link Plan#fluidId()} is the fluid the oil is locked to (for the bucket
     * it makes), empty for the others.
     */
    public static Plan discharge(PipeKind pipe, Situation situation, Supply supply) {
        Plan common = commonRefusal(situation);
        if (common != null) {
            return common;
        }
        FluidKind kind = pipe.fluid();
        FluidStore store = situation.store();
        if (store.amount(kind) < pipe.unitMb() || (kind.locksFluid() && store.lockedFluid().isEmpty())) {
            return refuse(store, Notice.CANNOT_PLACE_EMPTY);
        }
        if (!supply.productExists()) {
            return refuse(store, Notice.NO_BUCKET_FOR_FLUID);
        }
        if (!supply.hasEmptyContainer()) {
            return refuse(store, Notice.NO_EMPTY_CONTAINER);
        }
        String fluid = kind.locksFluid() ? store.lockedFluid() : "";
        FluidStoreOps.Drain drain = FluidStoreOps.drain(store, kind, pipe.unitMb());
        boolean empty = drain.store().amount(kind) <= 0;
        return new Plan(Action.DO, drain.store(), fluid, empty ? Notice.TANK_EMPTY_NOW : null);
    }

    private static Plan commonRefusal(Situation situation) {
        if (!situation.backpackFound()) {
            return refuse(situation.store(), situation.backpackProtected() ? Notice.BACKPACK_PROTECTED : Notice.NO_BACKPACK);
        }
        if (situation.tankTier() == null) {
            return refuse(situation.store(), Notice.NO_TANK);
        }
        return null;
    }

    private static Plan refuse(FluidStore store, Notice notice) {
        return new Plan(Action.REFUSE, store, "", notice);
    }
}
