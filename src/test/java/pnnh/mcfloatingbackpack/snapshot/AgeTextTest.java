package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 030: how long ago something was, in a few characters that need no translation. */
class AgeTextTest {
    private static final long MIN = 60_000L;
    private static final long HOUR = 60 * MIN;
    private static final long DAY = 24 * HOUR;

    @Test
    void underAMinuteIsLessThanAMinute() {
        assertEquals("<1m", AgeText.ago(0));
        assertEquals("<1m", AgeText.ago(59_999));
        assertEquals("<1m", AgeText.ago(-5000), "a clock that went backwards");
    }

    @Test
    void minutesHoursAndDays() {
        assertEquals("1m", AgeText.ago(MIN));
        assertEquals("59m", AgeText.ago(59 * MIN + 59_000));
        assertEquals("1h 0m", AgeText.ago(HOUR));
        assertEquals("5h 30m", AgeText.ago(5 * HOUR + 30 * MIN));
        assertEquals("1d 0h", AgeText.ago(DAY));
        assertEquals("3d 4h", AgeText.ago(3 * DAY + 4 * HOUR + 59 * MIN));
        assertEquals("400d 0h", AgeText.ago(400 * DAY));
    }
}
