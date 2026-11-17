package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 057: the config's numbers are moved into their range; the timed interval has a default and a debug override. */
class SnapshotSettingsTest {
    @Test
    void aNumberInRangeIsKept() {
        SnapshotSettings settings = SnapshotSettings.from(true, 768, null);
        assertEquals(768, settings.maxTotalMb());
        assertFalse(settings.clamped());
        assertTrue(settings.enabled());
        assertEquals(768L * 1024 * 1024, settings.maxTotalBytes());
    }

    @Test
    void aNumberOutsideIsMovedToTheNearestEndAndSaysSo() {
        assertEquals(128, SnapshotSettings.from(true, 1, null).maxTotalMb());
        assertTrue(SnapshotSettings.from(true, 1, null).clamped());
        assertEquals(128, SnapshotSettings.from(true, -50, null).maxTotalMb());
        assertEquals(4096, SnapshotSettings.from(true, 100_000, null).maxTotalMb());
        assertEquals(4096, SnapshotSettings.from(true, Integer.MAX_VALUE, null).maxTotalMb());
        assertEquals(128, SnapshotSettings.from(true, 128, null).maxTotalMb());
        assertFalse(SnapshotSettings.from(true, 128, null).clamped());
        assertEquals(4096, SnapshotSettings.from(true, 4096, null).maxTotalMb());
        assertFalse(SnapshotSettings.from(true, 4096, null).clamped());
    }

    @Test
    void theTimedIntervalIsFifteenMinutesUnlessTheDebugSwitchSaysOtherwise() {
        assertEquals(15 * 60 * 1000L, SnapshotSettings.from(true, 768, null).timedIntervalMs());
        assertEquals(5_000L, SnapshotSettings.from(true, 768, 5).timedIntervalMs());
        assertEquals(15 * 60 * 1000L, SnapshotSettings.from(true, 768, 0).timedIntervalMs());
        assertEquals(15 * 60 * 1000L, SnapshotSettings.from(true, 768, -3).timedIntervalMs());
    }

    @Test
    void theSwitchIsCarriedThrough() {
        assertFalse(SnapshotSettings.from(false, 768, null).enabled());
    }
}
