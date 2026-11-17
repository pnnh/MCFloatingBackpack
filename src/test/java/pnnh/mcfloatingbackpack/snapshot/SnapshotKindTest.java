package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 057: the three kinds of snapshot, how many of each a backpack keeps, and how they are named. */
class SnapshotKindTest {
    @Test
    void aBackpackKeepsSixtyTimedTwentyChangeAndFiveUpgradeSnapshots() {
        assertEquals(60, SnapshotKind.TIMED.keep());
        assertEquals(20, SnapshotKind.CHANGE.keep());
        assertEquals(5, SnapshotKind.UPGRADE.keep());
        assertEquals(85, SnapshotKind.TIMED.keep() + SnapshotKind.CHANGE.keep() + SnapshotKind.UPGRADE.keep());
    }

    @Test
    void onlyTheUpgradeKindIsProtected() {
        assertFalse(SnapshotKind.TIMED.isProtected());
        assertFalse(SnapshotKind.CHANGE.isProtected());
        assertTrue(SnapshotKind.UPGRADE.isProtected());
    }

    @Test
    void bytesAndSuffixesAreOneToOneAndZeroIsNotAKind() {
        for (SnapshotKind kind : SnapshotKind.values()) {
            assertEquals(kind, SnapshotKind.fromByte(kind.code()));
            assertEquals(kind, SnapshotKind.fromSuffix(kind.suffix()));
            assertEquals(kind, SnapshotKind.fromName(kind.name()));
        }
        assertNull(SnapshotKind.fromByte((byte) 0));
        assertNull(SnapshotKind.fromByte((byte) 4));
        assertNull(SnapshotKind.fromSuffix("-x"));
        assertNull(SnapshotKind.fromName(null));
        assertNull(SnapshotKind.fromName("timed"));
    }

    @Test
    void theOrdinaryKindHasNoSuffixSoOldFilesAreThatKind() {
        assertEquals("", SnapshotKind.CHANGE.suffix());
        assertEquals("-t", SnapshotKind.TIMED.suffix());
        assertEquals("-u", SnapshotKind.UPGRADE.suffix());
    }
}
