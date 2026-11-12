package pnnh.mcfloatingbackpack.tank;

/**
 * 048: the five liquids a tank can hold. This is the mod's own identity for a liquid: it does not have to match a registered
 * fluid (honey has none in vanilla or NeoForge, and NeoForge's milk fluid is off unless some mod enables it). Only the crude
 * oil remembers which actual fluid it is (see {@link FluidStore}), because different mods add different crude oils.
 * Pure logic, no Minecraft types.
 */
public enum FluidKind {
    WATER("water", 1000, 0xFF3F76E4, false),
    OIL("oil", 1000, 0xFF6A5A7A, true),
    LAVA("lava", 1000, 0xFFE8651A, false),
    MILK("milk", 1000, 0xFFF2F2F2, false),
    HONEY("honey", 250, 0xFFE3A82B, false);

    private final String id;
    private final int perUseMb;
    private final int color;
    private final boolean locksFluid;

    FluidKind(String id, int perUseMb, int color, boolean locksFluid) {
        this.id = id;
        this.perUseMb = perUseMb;
        this.color = color;
        this.locksFluid = locksFluid;
    }

    /** The name used in item ids and in the stored data. */
    public String id() {
        return id;
    }

    /** How much one action moves (049, 050): a bucket, or a bottle for honey. */
    public int perUseMb() {
        return perUseMb;
    }

    /** ARGB colour of the level bar. */
    public int color() {
        return color;
    }

    /** Whether the tank remembers which fluid it holds (only the crude oil does). */
    public boolean locksFluid() {
        return locksFluid;
    }

    /** @return {@code null} for an id that is not a liquid */
    public static FluidKind byId(String id) {
        for (FluidKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
