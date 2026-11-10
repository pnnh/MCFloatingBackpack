package pnnh.mcfloatingbackpack.snapshot.net;

/** 031: which backpacks the list shows: the requester's own, or (permission level 2 only) everybody's. Pure data. */
public enum Scope {
    MINE((byte) 0),
    ALL((byte) 1);

    private final byte code;

    Scope(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    /** @return the scope a byte stands for, or null if it is not one (a request carrying it is refused) */
    public static Scope fromByte(byte code) {
        for (Scope scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        return null;
    }
}
