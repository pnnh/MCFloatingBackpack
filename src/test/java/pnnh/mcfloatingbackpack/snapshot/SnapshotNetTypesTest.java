package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.net.ActionKind;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;

/** 031: the codes that travel as bytes round-trip, and an unknown byte is refused rather than guessed at. */
class SnapshotNetTypesTest {
    @Test
    void scopesRoundTripAndUnknownBytesAreRefused() {
        for (Scope scope : Scope.values()) {
            assertEquals(scope, Scope.fromByte(scope.code()));
        }
        assertNull(Scope.fromByte((byte) 2));
        assertNull(Scope.fromByte((byte) -1));
    }

    @Test
    void actionKindsRoundTripAndUnknownBytesAreRefused() {
        for (ActionKind kind : ActionKind.values()) {
            assertEquals(kind, ActionKind.fromByte(kind.code()));
        }
        assertNull(ActionKind.fromByte((byte) 5));
        assertNull(ActionKind.fromByte((byte) 99));
    }

    @Test
    void resultCodesRoundTripHaveDistinctBytesAndKnowIfTheySucceeded() {
        Set<Byte> seen = new HashSet<>();
        for (ResultCode code : ResultCode.values()) {
            assertEquals(code, ResultCode.fromByte(code.code()));
            assertTrue(seen.add(code.code()), "duplicate byte for " + code);
        }
        assertNull(ResultCode.fromByte((byte) 100));
        assertTrue(ResultCode.RESTORED.success());
        assertTrue(ResultCode.TRIM_PREVIEWED.success());
        assertEquals(false, ResultCode.NO_PERMISSION.success());
        assertEquals(false, ResultCode.UNAVAILABLE.success());
    }
}
