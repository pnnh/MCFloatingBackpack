package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 030: which snapshots of one backpack are deleted when there are more than are kept. */
class SnapshotPolicyTest {
    @Test
    void fiveAreKept() {
        assertEquals(5, SnapshotPolicy.KEEP);
    }

    @Test
    void nothingGoesWhileThereAreNoMoreThanKept() {
        assertTrue(SnapshotPolicy.toPrune(List.of(), 5).isEmpty());
        assertTrue(SnapshotPolicy.toPrune(List.of(3L, 1L, 2L), 5).isEmpty());
        assertTrue(SnapshotPolicy.toPrune(List.of(1L, 2L, 3L, 4L, 5L), 5).isEmpty());
    }

    @Test
    void theOldestGoWhateverTheOrderTheyAreGivenIn() {
        assertEquals(List.of(2L, 1L), SnapshotPolicy.toPrune(List.of(5L, 1L, 7L, 3L, 6L, 2L, 4L), 5));
        assertEquals(List.of(1L), SnapshotPolicy.toPrune(List.of(1L, 2L, 3L, 4L, 5L, 6L), 5));
    }

    @Test
    void keepingZeroDeletesAll() {
        assertEquals(2, SnapshotPolicy.toPrune(List.of(1L, 2L), 0).size());
    }
}
