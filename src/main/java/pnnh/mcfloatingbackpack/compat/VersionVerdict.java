package pnnh.mcfloatingbackpack.compat;

/** 058: how an installed version stands against the stretches this mod was verified with. */
public enum VersionVerdict {
    IN_RANGE,
    /** Older than every stretch. */
    BELOW_ALL,
    /** Newer than every stretch. */
    ABOVE_ALL,
    /** Between two stretches. */
    IN_GAP,
    /** The version text has no usable number. */
    UNKNOWN;

    public boolean outOfRange() {
        return this != IN_RANGE;
    }
}
