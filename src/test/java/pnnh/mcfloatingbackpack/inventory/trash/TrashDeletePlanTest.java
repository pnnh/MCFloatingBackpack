package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 022: how many groups an operation destroys and when that needs the player's confirmation. */
class TrashDeletePlanTest {
    @Test
    void thresholdIsNine() {
        assertEquals(9, TrashDeletePlan.THRESHOLD);
    }

    @Test
    void destroyedIsWhatDoesNotFitAfterTheOperation() {
        assertEquals(0, TrashDeletePlan.destroyed(0, 5));
        assertEquals(0, TrashDeletePlan.destroyed(4, 3));
        assertEquals(1, TrashDeletePlan.destroyed(9, 1));
        assertEquals(9, TrashDeletePlan.destroyed(9, 9));
        assertEquals(10, TrashDeletePlan.destroyed(9, 10));
        assertEquals(11, TrashDeletePlan.destroyed(0, 20));
        assertEquals(8, TrashDeletePlan.destroyed(5, 12));
        assertEquals(10, TrashDeletePlan.destroyed(5, 14));
    }

    @Test
    void negativeCountsCountAsZero() {
        assertEquals(0, TrashDeletePlan.destroyed(-3, 4));
        assertEquals(0, TrashDeletePlan.destroyed(4, -3));
        assertEquals(0, TrashDeletePlan.destroyed(-1, -1));
    }

    @Test
    void onlyMoreThanNineNeedsConfirmation() {
        assertFalse(TrashDeletePlan.needsConfirm(0));
        assertFalse(TrashDeletePlan.needsConfirm(9));
        assertTrue(TrashDeletePlan.needsConfirm(10));
    }

    @Test
    void oneGroupAtATimeNeverNeedsConfirmation() {
        for (int existing = 0; existing <= 9; existing++) {
            assertFalse(TrashDeletePlan.needsConfirm(TrashDeletePlan.destroyed(existing, 1)));
        }
    }
}
