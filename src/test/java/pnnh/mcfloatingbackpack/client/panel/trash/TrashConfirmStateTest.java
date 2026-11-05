package pnnh.mcfloatingbackpack.client.panel.trash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmState.Kind;

/** 022: the operation waiting for the player's yes or no. */
class TrashConfirmStateTest {
    private final Object screen = new Object();

    @BeforeEach
    void reset() {
        TrashConfirmState.get().cancel();
    }

    @Test
    void startsWithNothingPending() {
        TrashConfirmState state = TrashConfirmState.get();
        assertFalse(state.isPending());
        assertFalse(state.shouldDiscard(true, true, screen));
    }

    @Test
    void openRemembersWhatIsWaiting() {
        TrashConfirmState state = TrashConfirmState.get();
        state.open(Kind.MAIN_SLOTS, new int[] {3, 5}, 11, screen);
        assertTrue(state.isPending());
        assertEquals(Kind.MAIN_SLOTS, state.kind());
        assertArrayEquals(new int[] {3, 5}, state.slots());
        assertEquals(11, state.destroyed());
    }

    @Test
    void theSlotSnapshotIsACopy() {
        TrashConfirmState state = TrashConfirmState.get();
        int[] slots = {1, 2};
        state.open(Kind.MAIN_SLOTS, slots, 10, screen);
        slots[0] = 99;
        assertArrayEquals(new int[] {1, 2}, state.slots());
    }

    @Test
    void cancelForgetsEverything() {
        TrashConfirmState state = TrashConfirmState.get();
        state.open(Kind.DUMP, new int[0], 12, screen);
        state.cancel();
        assertFalse(state.isPending());
        assertEquals(0, state.destroyed());
    }

    @Test
    void isDiscardedWhenItsContextIsGone() {
        TrashConfirmState state = TrashConfirmState.get();
        state.open(Kind.DUMP, new int[0], 12, screen);
        assertFalse(state.shouldDiscard(true, true, screen));
        assertTrue(state.shouldDiscard(false, true, screen));
        assertTrue(state.shouldDiscard(true, false, screen));
        assertTrue(state.shouldDiscard(true, true, new Object()));
        assertTrue(state.shouldDiscard(true, true, null));
    }
}
