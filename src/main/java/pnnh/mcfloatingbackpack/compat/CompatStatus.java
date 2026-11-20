package pnnh.mcfloatingbackpack.compat;

/** 058: whether the active compatibility for one third-party mod is on in this run of the server. */
public enum CompatStatus {
    /** Installed and inside the verified versions. */
    ACTIVE(true),
    /** Installed, outside the verified versions, and not forced on: switched off. */
    OUT_OF_RANGE(false),
    /** Installed, outside the verified versions, but the server config allows untested versions: on. */
    FORCED(true),
    /** It failed during this run and was switched off for the rest of it. */
    FAILED(false);

    private final boolean on;

    CompatStatus(boolean on) {
        this.on = on;
    }

    public boolean isOn() {
        return on;
    }
}
