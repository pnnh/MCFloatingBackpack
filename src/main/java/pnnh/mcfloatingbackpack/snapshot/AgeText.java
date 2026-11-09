package pnnh.mcfloatingbackpack.snapshot;

/** 030: how long ago something was, in a few characters that need no translation ({@code <1m}, {@code 5m}, {@code 2h 10m}, {@code 3d 4h}). Pure logic. */
public final class AgeText {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private AgeText() {
    }

    public static String ago(long millis) {
        if (millis < MINUTE) {
            return "<1m";
        }
        if (millis < HOUR) {
            return (millis / MINUTE) + "m";
        }
        if (millis < DAY) {
            return (millis / HOUR) + "h " + ((millis % HOUR) / MINUTE) + "m";
        }
        return (millis / DAY) + "d " + ((millis % DAY) / HOUR) + "h";
    }
}
