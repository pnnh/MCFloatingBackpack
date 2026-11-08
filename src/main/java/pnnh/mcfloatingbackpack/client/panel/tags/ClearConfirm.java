package pnnh.mcfloatingbackpack.client.panel.tags;

/**
 * 025: the in-row two-step confirmation of "clear all tags". The first click arms a row, the second click on the same row
 * (before the timeout) confirms; arming another row replaces it, and anything else cancels. Time is passed in by the
 * caller, so this is plain logic with nothing to wait for in a test.
 */
public final class ClearConfirm {
    /** How long an armed row waits for its second click. */
    public static final long TIMEOUT_MS = 3000L;

    private String key;
    private long armedAt;

    public void arm(String key, long now) {
        this.key = key;
        this.armedAt = now;
    }

    public boolean isArmed(String key, long now) {
        return this.key != null && this.key.equals(key) && now - armedAt < TIMEOUT_MS;
    }

    /** @return true if this click confirms an armed row (and resets); false if the caller should arm instead */
    public boolean confirm(String key, long now) {
        if (isArmed(key, now)) {
            cancel();
            return true;
        }
        return false;
    }

    public void cancel() {
        key = null;
        armedAt = 0L;
    }
}
