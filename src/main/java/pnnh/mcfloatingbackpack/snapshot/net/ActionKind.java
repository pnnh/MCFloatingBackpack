package pnnh.mcfloatingbackpack.snapshot.net;

/** 031: what an action request asks for. Every one of them needs permission level 2. Pure data. */
public enum ActionKind {
    RESTORE((byte) 0),
    DELETE_ONE((byte) 1),
    DELETE_ALL((byte) 2),
    TRIM_PREVIEW((byte) 3),
    TRIM_CONFIRM((byte) 4);

    private final byte code;

    ActionKind(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    /** @return the kind a byte stands for, or null if it is not one (a request carrying it is refused) */
    public static ActionKind fromByte(byte code) {
        for (ActionKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return null;
    }
}
