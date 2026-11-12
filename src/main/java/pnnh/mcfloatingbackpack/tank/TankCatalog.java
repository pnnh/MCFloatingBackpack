package pnnh.mcfloatingbackpack.tank;

import java.util.ArrayList;
import java.util.List;

import pnnh.mcfloatingbackpack.item.BackpackTier;

/**
 * 048: which tanks exist, how much each holds, and which backpack tier takes which tank tier. A tank's tier is a
 * {@link BackpackTier}; only leather, iron, gold and netherite have tanks (no diamond), and no leather lava tank.
 * Pure logic, no Minecraft types.
 */
public final class TankCatalog {
    /** One kind of tank. */
    public record Tank(FluidKind kind, BackpackTier tier) {
    }

    private static final List<BackpackTier> TIERS =
            List.of(BackpackTier.LEATHER, BackpackTier.IRON, BackpackTier.GOLD, BackpackTier.NETHERITE);
    private static final List<Tank> ALL = buildAll();

    private TankCatalog() {
    }

    private static List<Tank> buildAll() {
        List<Tank> all = new ArrayList<>();
        for (BackpackTier tier : TIERS) {
            for (FluidKind kind : FluidKind.values()) {
                if (exists(kind, tier)) {
                    all.add(new Tank(kind, tier));
                }
            }
        }
        return List.copyOf(all);
    }

    /** The tank tiers, lowest first. */
    public static List<BackpackTier> tiers() {
        return TIERS;
    }

    /** Whether a tank of this kind and tier exists: every combination of the four tiers and five liquids but the leather lava tank. */
    public static boolean exists(FluidKind kind, BackpackTier tier) {
        return TIERS.contains(tier) && !(kind == FluidKind.LAVA && tier == BackpackTier.LEATHER);
    }

    /** All 19 tanks, tier first and then liquid. */
    public static List<Tank> all() {
        return ALL;
    }

    /** What a tank of this tier holds, in millibuckets (1 bucket = 1000). */
    public static int capacityMb(BackpackTier tier) {
        return switch (tier) {
            case LEATHER -> 8 * 1000;
            case IRON -> 16 * 1000;
            case GOLD -> 64 * 1000;
            case NETHERITE -> 256 * 1000;
            default -> throw new IllegalArgumentException("no tank tier " + tier);
        };
    }

    /** The item id (without the mod namespace): {@code <tier>_<liquid>_tank}. */
    public static String itemId(FluidKind kind, BackpackTier tier) {
        return tier.name().toLowerCase(java.util.Locale.ROOT) + "_" + kind.id() + "_tank";
    }

    /**
     * Whether a tank of this tier may be put into a backpack of the given tier: the tank's tier must not be higher. A backpack
     * whose tier is unknown ({@code null}) takes only leather tanks.
     */
    public static boolean allowedIn(BackpackTier tank, BackpackTier backpack) {
        if (backpack == null) {
            return tank == BackpackTier.LEATHER;
        }
        return tank.ordinal() <= backpack.ordinal();
    }
}
