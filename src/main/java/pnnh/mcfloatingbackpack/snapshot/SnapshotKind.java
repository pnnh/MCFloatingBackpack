package pnnh.mcfloatingbackpack.snapshot;

/**
 * 057: the three kinds of backpack snapshot. {@code TIMED} is written every 15 minutes for the backpacks that changed since the last
 * timed one, {@code CHANGE} is what 030 always wrote (a little after a change, when the player leaves, when the server stops), and
 * {@code UPGRADE} is not written but made: the newest snapshot of a backpack when the mod's version changed is pinned as one, so what
 * the old version wrote is kept. How many of each a backpack keeps, and the suffix of the file name, are here. Pure logic.
 */
public enum SnapshotKind {
    TIMED((byte) 1, "-t", 60),
    CHANGE((byte) 2, "", 20),
    UPGRADE((byte) 3, "-u", 5);

    private final byte code;
    private final String suffix;
    private final int keep;

    SnapshotKind(byte code, String suffix, int keep) {
        this.code = code;
        this.suffix = suffix;
        this.keep = keep;
    }

    /** The byte of the protocol (0 is "all" there and is not a kind). */
    public byte code() {
        return code;
    }

    /** What follows the time in the file name: {@code -t}, nothing, or {@code -u}. */
    public String suffix() {
        return suffix;
    }

    /** How many snapshots of this kind one backpack keeps. */
    public int keep() {
        return keep;
    }

    /** Whether the capacity clean-up and the other kinds' retention must leave it alone. */
    public boolean isProtected() {
        return this == UPGRADE;
    }

    /** @return the kind a byte stands for, or null (also for 0, "all") */
    public static SnapshotKind fromByte(byte code) {
        for (SnapshotKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return null;
    }

    /** @return the kind a file name suffix stands for ({@code ""} is {@link #CHANGE}), or null for a suffix that is not ours */
    public static SnapshotKind fromSuffix(String suffix) {
        for (SnapshotKind kind : values()) {
            if (kind.suffix.equals(suffix)) {
                return kind;
            }
        }
        return null;
    }

    /** @return the kind a header's {@code kind} text names, or null when it is missing or not one of ours */
    public static SnapshotKind fromName(String name) {
        for (SnapshotKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }
}
