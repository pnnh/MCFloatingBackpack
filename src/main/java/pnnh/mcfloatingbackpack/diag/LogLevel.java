package pnnh.mcfloatingbackpack.diag;

/**
 * How much of this mod's own diagnostics is written to the log (the {@code logging.level} setting, one in the client config
 * and one in the server config). Each level includes the ones before it; {@link #OFF} writes none. Pure logic.
 */
public enum LogLevel {
    OFF,
    ERROR,
    WARN,
    INFO,
    DEBUG;

    /** Whether a message of level {@code message} is written when this level is the setting. */
    public boolean allows(LogLevel message) {
        return message != OFF && message.ordinal() <= ordinal();
    }
}
