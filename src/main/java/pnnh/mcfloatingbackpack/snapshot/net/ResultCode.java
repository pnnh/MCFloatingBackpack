package pnnh.mcfloatingbackpack.snapshot.net;

/**
 * 031: how an action, or a preview, turned out. The server sends the code and a few plain arguments; the client turns it into
 * a line of text in the player's language. Pure data.
 */
public enum ResultCode {
    // successes
    RESTORED((byte) 0, true),
    DELETED_ONE((byte) 1, true),
    DELETED_ALL((byte) 2, true),
    TRIM_PREVIEWED((byte) 3, true),
    TRIM_DONE((byte) 4, true),
    // failures
    NOT_FOUND((byte) 10, false),
    NO_PERMISSION((byte) 11, false),
    BAD_VERSION((byte) 12, false),
    TARGET_OFFLINE((byte) 13, false),
    NO_TIER((byte) 14, false),
    UNREADABLE((byte) 15, false),
    TOO_LARGE((byte) 16, false),
    FAILED((byte) 17, false),
    UNAVAILABLE((byte) 18, false);

    private final byte code;
    private final boolean success;

    ResultCode(byte code, boolean success) {
        this.code = code;
        this.success = success;
    }

    public byte code() {
        return code;
    }

    public boolean success() {
        return success;
    }

    /** @return the code a byte stands for, or null if it is not one */
    public static ResultCode fromByte(byte code) {
        for (ResultCode result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return null;
    }
}
