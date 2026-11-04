package pnnh.mcfloatingbackpack.item;

/**
 * Five backpack tiers. Capacities are part of the item identity.
 * Minecraft types stay out of this enum so unit tests can load it directly.
 */
public enum BackpackTier {
    LEATHER("leather_floating_backpack", 36),
    IRON("iron_floating_backpack", 54),
    GOLD("gold_floating_backpack", 72),
    DIAMOND("diamond_floating_backpack", 90),
    NETHERITE("netherite_floating_backpack", 108);

    public static final int COLUMNS = 9;

    private final String id;
    private final int capacity;

    BackpackTier(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public String id() {
        return id;
    }

    public int capacity() {
        return capacity;
    }

    public int rows() {
        return capacity / COLUMNS;
    }

    public BackpackTier next() {
        int next = ordinal() + 1;
        BackpackTier[] values = values();
        return next < values.length ? values[next] : null;
    }

    /** @return the tier whose capacity exactly matches, or {@code null} if none does. */
    public static BackpackTier byCapacity(int capacity) {
        for (BackpackTier tier : values()) {
            if (tier.capacity() == capacity) {
                return tier;
            }
        }
        return null;
    }
}
