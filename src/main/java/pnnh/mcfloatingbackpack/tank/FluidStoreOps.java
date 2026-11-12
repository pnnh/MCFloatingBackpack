package pnnh.mcfloatingbackpack.tank;

/**
 * 048: how liquid goes into and out of a {@link FluidStore}. Nothing in the game calls this yet (049 and 050 do); it is here so the
 * rules (the capacity, the crude oil staying one fluid until it is empty, nothing lost or made up) are tested once. Pure functions.
 */
public final class FluidStoreOps {
    public enum Reason { OK, FULL, LOCKED_OTHER, EMPTY }

    /** What a deposit did: the new store, how much went in, and why not all of it did. */
    public record Deposit(FluidStore store, int accepted, Reason reason) {
    }

    /** What a drain did: the new store, how much came out, the oil's fluid at that moment (empty for other liquids), and why not all of it did. */
    public record Drain(FluidStore store, int removed, String fluidId, Reason reason) {
    }

    private FluidStoreOps() {
    }

    /** Whether the liquid can go in at all: crude oil that is locked to another fluid cannot. */
    public static boolean canAccept(FluidStore store, FluidKind kind, String fluidId) {
        if (!kind.locksFluid()) {
            return true;
        }
        String locked = store.lockedFluid();
        return store.amount(kind) <= 0 || locked.isEmpty() || locked.equals(fluidId);
    }

    /** Puts in up to {@code mb}, as far as {@code capacityMb} allows; the first oil in locks the fluid. */
    public static Deposit deposit(FluidStore store, FluidKind kind, String fluidId, int mb, int capacityMb) {
        if (mb <= 0) {
            return new Deposit(store, 0, Reason.OK);
        }
        if (!canAccept(store, kind, fluidId)) {
            return new Deposit(store, 0, Reason.LOCKED_OTHER);
        }
        int current = store.amount(kind);
        int room = Math.max(0, capacityMb - current);
        int accepted = Math.min(mb, room);
        if (accepted <= 0) {
            return new Deposit(store, 0, Reason.FULL);
        }
        String lock = kind.locksFluid() ? (current > 0 && !store.lockedFluid().isEmpty() ? store.lockedFluid() : fluidId) : "";
        FluidStore next = store.with(kind, current + accepted, lock);
        return new Deposit(next, accepted, accepted < mb ? Reason.FULL : Reason.OK);
    }

    /** Takes out up to {@code mb}; oil that is used up unlocks. */
    public static Drain drain(FluidStore store, FluidKind kind, int mb) {
        int current = store.amount(kind);
        String fluid = kind.locksFluid() ? store.lockedFluid() : "";
        if (mb <= 0) {
            return new Drain(store, 0, fluid, Reason.OK);
        }
        if (current <= 0) {
            return new Drain(store, 0, fluid, Reason.EMPTY);
        }
        int removed = Math.min(mb, current);
        FluidStore next = store.with(kind, current - removed, fluid);
        return new Drain(next, removed, fluid, removed < mb ? Reason.EMPTY : Reason.OK);
    }
}
