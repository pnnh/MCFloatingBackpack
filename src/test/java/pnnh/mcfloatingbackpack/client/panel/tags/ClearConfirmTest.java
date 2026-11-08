package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 025: the two-step "clear all tags" confirmation of one list row. Time is passed in, so nothing waits. */
class ClearConfirmTest {
    @Test
    void nothingIsArmedAtFirst() {
        ClearConfirm confirm = new ClearConfirm();
        assertFalse(confirm.isArmed("a", 0));
        assertFalse(confirm.confirm("a", 0));
    }

    @Test
    void armingOneRowDoesNotArmAnother() {
        ClearConfirm confirm = new ClearConfirm();
        confirm.arm("a", 0);
        assertTrue(confirm.isArmed("a", 0));
        assertFalse(confirm.isArmed("b", 0));
    }

    @Test
    void armingAnotherRowReplacesTheFirst() {
        ClearConfirm confirm = new ClearConfirm();
        confirm.arm("a", 0);
        confirm.arm("b", 100);
        assertFalse(confirm.isArmed("a", 100));
        assertTrue(confirm.isArmed("b", 100));
    }

    @Test
    void confirmingBeforeTheTimeoutSucceedsOnceAndResets() {
        ClearConfirm confirm = new ClearConfirm();
        confirm.arm("a", 0);
        assertTrue(confirm.confirm("a", ClearConfirm.TIMEOUT_MS - 1));
        assertFalse(confirm.isArmed("a", ClearConfirm.TIMEOUT_MS - 1));
        assertFalse(confirm.confirm("a", ClearConfirm.TIMEOUT_MS - 1));
    }

    @Test
    void confirmingAfterTheTimeoutFails() {
        ClearConfirm confirm = new ClearConfirm();
        confirm.arm("a", 0);
        assertFalse(confirm.isArmed("a", ClearConfirm.TIMEOUT_MS));
        assertFalse(confirm.confirm("a", ClearConfirm.TIMEOUT_MS));
    }

    @Test
    void confirmingAnotherRowFailsAndLeavesTheArmedOne() {
        ClearConfirm confirm = new ClearConfirm();
        confirm.arm("a", 0);
        assertFalse(confirm.confirm("b", 10));
        assertTrue(confirm.isArmed("a", 10));
    }

    @Test
    void cancelResets() {
        ClearConfirm confirm = new ClearConfirm();
        confirm.arm("a", 0);
        confirm.cancel();
        assertFalse(confirm.isArmed("a", 0));
    }

    @Test
    void theTimeoutIsThreeSeconds() {
        assertEquals(3000L, ClearConfirm.TIMEOUT_MS);
    }
}
