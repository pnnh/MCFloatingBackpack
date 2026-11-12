package pnnh.mcfloatingbackpack.tank;

import java.util.EnumMap;
import java.util.Map;

/**
 * 048: what a backpack holds of each liquid, in millibuckets, and which fluid its crude oil is. It belongs to the backpack, not
 * to a tank item: taking a tank out leaves the liquid in the backpack, and a tank of that liquid put back shows it again.
 *
 * <p>Invariants (enforced by {@link #of}): every amount is positive (a missing one is 0); the oil fluid is non-empty only while there
 * is oil, and no other liquid has one. There is no upper limit here: the capacity comes from the tank, and a store that holds more
 * than a tank takes (from older data) is kept as it is, never trimmed. Immutable. Pure logic, no Minecraft types.
 */
public record FluidStore(Map<FluidKind, Integer> amounts, String oilFluid) {
    public static final FluidStore EMPTY = new FluidStore(Map.of(), "");

    public FluidStore {
        amounts = amounts.isEmpty() ? Map.of() : Map.copyOf(amounts);
    }

    /** A store cleaned to the invariants: non-positive amounts dropped, a lock without oil (or a lock on another liquid) removed. */
    public static FluidStore of(Map<FluidKind, Integer> amounts, String oilFluid) {
        Map<FluidKind, Integer> clean = new EnumMap<>(FluidKind.class);
        if (amounts != null) {
            for (Map.Entry<FluidKind, Integer> entry : amounts.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                    clean.put(entry.getKey(), entry.getValue());
                }
            }
        }
        String lock = clean.containsKey(FluidKind.OIL) && oilFluid != null ? oilFluid : "";
        return new FluidStore(clean, lock);
    }

    public int amount(FluidKind kind) {
        return amounts.getOrDefault(kind, 0);
    }

    /** The fluid the crude oil is locked to; empty when there is no oil. */
    public String lockedFluid() {
        return oilFluid;
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /**
     * The same store with {@code kind} set to {@code mb} (zero or less removes it). For oil, {@code fluidId} is the fluid it
     * now is; running out of oil clears the lock.
     */
    public FluidStore with(FluidKind kind, int mb, String fluidId) {
        Map<FluidKind, Integer> next = new EnumMap<>(FluidKind.class);
        next.putAll(amounts);
        if (mb > 0) {
            next.put(kind, mb);
        } else {
            next.remove(kind);
        }
        String lock = kind == FluidKind.OIL ? (mb > 0 && fluidId != null ? fluidId : "") : oilFluid;
        return of(next, lock);
    }
}
