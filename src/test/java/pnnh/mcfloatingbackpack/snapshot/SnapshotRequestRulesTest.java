package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 031: what a request from a client may say. */
class SnapshotRequestRulesTest {
    @Test
    void pages() {
        assertFalse(SnapshotRequestRules.validPage(0));
        assertFalse(SnapshotRequestRules.validPage(-1));
        assertTrue(SnapshotRequestRules.validPage(1));
        assertTrue(SnapshotRequestRules.validPage(1000));
        assertFalse(SnapshotRequestRules.validPage(1001));
        assertFalse(SnapshotRequestRules.validPage(Integer.MAX_VALUE));
    }

    @Test
    void pageSizes() {
        assertFalse(SnapshotRequestRules.validPageSize(0));
        assertTrue(SnapshotRequestRules.validPageSize(1));
        assertTrue(SnapshotRequestRules.validPageSize(16));
        assertFalse(SnapshotRequestRules.validPageSize(17));
        assertFalse(SnapshotRequestRules.validPageSize(-5));
    }

    @Test
    void versionNumbers() {
        assertFalse(SnapshotRequestRules.validVersion(0));
        assertTrue(SnapshotRequestRules.validVersion(1));
        assertTrue(SnapshotRequestRules.validVersion(16));
        assertFalse(SnapshotRequestRules.validVersion(17));
    }

    @Test
    void daysOfATrimMayBeZeroAndTheDaysOfAFilterMayNotBe() {
        assertTrue(SnapshotRequestRules.validTrimDays(0));
        assertTrue(SnapshotRequestRules.validTrimDays(36500));
        assertFalse(SnapshotRequestRules.validTrimDays(-1));
        assertFalse(SnapshotRequestRules.validTrimDays(36501));
        assertTrue(SnapshotRequestRules.validFilterDays(-1));
        assertFalse(SnapshotRequestRules.validFilterDays(0));
        assertTrue(SnapshotRequestRules.validFilterDays(1));
        assertFalse(SnapshotRequestRules.validFilterDays(-2));
        assertFalse(SnapshotRequestRules.validFilterDays(36501));
    }

    @Test
    void names() {
        assertTrue(SnapshotRequestRules.validName(""));
        assertTrue(SnapshotRequestRules.validName("Dev"));
        assertTrue(SnapshotRequestRules.validName("Player_1"));
        assertTrue(SnapshotRequestRules.validName("a".repeat(16)));
        assertFalse(SnapshotRequestRules.validName("a".repeat(17)));
        assertFalse(SnapshotRequestRules.validName(null));
        for (String bad : new String[] {"a b", "a-b", "名字", "a\nb", "a/b", "a.b", "<x>"}) {
            assertFalse(SnapshotRequestRules.validName(bad), bad);
        }
    }

    @Test
    void thePreviewLimitsAreWhatTheGameCanCarry() {
        assertTrue(SnapshotRequestRules.MAX_PREVIEW_SLOTS == 108);
        assertTrue(SnapshotRequestRules.MAX_PREVIEW_BYTES < 1_048_576);
    }
}
