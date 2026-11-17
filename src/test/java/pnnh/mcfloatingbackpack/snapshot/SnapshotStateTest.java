package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 057: the small file the snapshot folder keeps between runs, and what a damaged one means. */
class SnapshotStateTest {
    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void theTextRoundTrips() {
        SnapshotState state = new SnapshotState("0.2.0", 3, "0.1.0", 7, List.of(A, B));
        assertEquals(Optional.of(state), SnapshotState.parse(state.toText()));
        SnapshotState empty = new SnapshotState("0.2.0", 1, "", 0, List.of());
        assertEquals(Optional.of(empty), SnapshotState.parse(empty.toText()));
    }

    @Test
    void aMissingOrDamagedFileMeansNoRecord() {
        assertEquals(Optional.empty(), SnapshotState.parse(null));
        assertEquals(Optional.empty(), SnapshotState.parse(""));
        assertEquals(Optional.empty(), SnapshotState.parse("hello"));
        assertEquals(Optional.empty(), SnapshotState.parse("change_id=3"), "no version");
        assertEquals(Optional.empty(), SnapshotState.parse("last_mod_version=1\nchange_id=abc"), "the change number is not a number");
        assertEquals(Optional.empty(), SnapshotState.parse("last_mod_version=1"), "no change number");
        assertEquals(Optional.empty(), SnapshotState.parse("last_mod_version=\nchange_id=1"), "an empty version");
        assertEquals(Optional.empty(), SnapshotState.parse("last_mod_version=1\nchange_id=-2"));
    }

    @Test
    void aBadPlayerOrNumberIsLeftOutAndTheRestIsKept() {
        SnapshotState state = SnapshotState.parse("# c\nlast_mod_version = 0.2.0 \nchange_id=2\npinned=x\nnotified=" + A + ",nonsense,," + A + "," + B + "\nunknown=1\n").orElseThrow();
        assertEquals("0.2.0", state.lastModVersion());
        assertEquals(2, state.changeId());
        assertEquals(0, state.pinned());
        assertEquals(List.of(A, B), state.notified(), "no bad ones, no duplicates");
    }

    @Test
    void addingAPlayerKeepsTheOrderAndIsIdempotent() {
        SnapshotState state = new SnapshotState("1", 1, "", 0, List.of(A));
        assertEquals(List.of(A, B), state.withNotified(B).notified());
        assertTrue(state.withNotified(A) == state);
    }

    @Test
    void theListIsCappedAndTheOldestAreDropped() {
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < SnapshotState.MAX_NOTIFIED + 5; i++) {
            many.add(new UUID(0, i));
        }
        SnapshotState state = new SnapshotState("1", 1, "", 0, many);
        assertEquals(SnapshotState.MAX_NOTIFIED, state.notified().size());
        assertEquals(new UUID(0, 5), state.notified().get(0));
        assertTrue(state.full());
        assertFalse(new SnapshotState("1", 1, "", 0, List.of()).full());
        SnapshotState added = state.withNotified(new UUID(1, 1));
        assertEquals(SnapshotState.MAX_NOTIFIED, added.notified().size());
        assertEquals(new UUID(0, 6), added.notified().get(0));
    }
}
