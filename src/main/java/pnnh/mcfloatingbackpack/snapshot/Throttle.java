package pnnh.mcfloatingbackpack.snapshot;

/** 057: lets something through at most once in a period (a warning that must not fill the log). Pure logic; one thread uses it. */
public final class Throttle {
    public static final long HOUR_MS = 60L * 60 * 1000;

    private final long periodMs;
    private boolean used;
    private long last;

    public Throttle(long periodMs) {
        this.periodMs = periodMs;
    }

    /** @return true if the first call, or the last one that returned true was at least a period ago */
    public boolean tryAcquire(long now) {
        if (!used || now - last >= periodMs) {
            used = true;
            last = now;
            return true;
        }
        return false;
    }
}
