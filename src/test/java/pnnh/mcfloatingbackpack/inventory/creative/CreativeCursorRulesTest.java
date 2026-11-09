package pnnh.mcfloatingbackpack.inventory.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.creative.CreativeCursorRules.Verdict;

/** 027: which cursor reports a creative player's client may make. */
class CreativeCursorRulesTest {
    private static Verdict check(boolean creative, boolean ownMenu, boolean empty, int count, int max, boolean enabled) {
        return CreativeCursorRules.check(creative, ownMenu, empty, count, max, enabled);
    }

    @Test
    void anEmptyCursorIsAlwaysFineForACreativePlayerWithTheirOwnMenu() {
        assertEquals(Verdict.ACCEPT, check(true, true, true, 0, 64, true));
    }

    @Test
    void aLegalStackIsAccepted() {
        assertEquals(Verdict.ACCEPT, check(true, true, false, 64, 64, true));
        assertEquals(Verdict.ACCEPT, check(true, true, false, 1, 64, true));
        assertEquals(Verdict.ACCEPT, check(true, true, false, 16, 16, true));
        assertEquals(Verdict.ACCEPT, check(true, true, false, 1, 1, true));
    }

    @Test
    void aPlayerWhoIsNotInCreativeIsIgnored() {
        assertEquals(Verdict.NOT_CREATIVE, check(false, true, true, 0, 64, true));
        assertEquals(Verdict.NOT_CREATIVE, check(false, true, false, 10, 64, true));
    }

    @Test
    void anotherOpenMenuIsIgnored() {
        assertEquals(Verdict.OTHER_MENU, check(true, false, true, 0, 64, true));
        assertEquals(Verdict.OTHER_MENU, check(true, false, false, 10, 64, true));
    }

    @Test
    void moreThanTheItemStacksToIsRefused() {
        assertEquals(Verdict.BAD_COUNT, check(true, true, false, 65, 64, true));
        assertEquals(Verdict.BAD_COUNT, check(true, true, false, 17, 16, true));
        assertEquals(Verdict.BAD_COUNT, check(true, true, false, 2, 1, true));
    }

    @Test
    void aNegativeCountIsRefused() {
        assertEquals(Verdict.BAD_COUNT, check(true, true, false, -1, 64, true));
    }

    @Test
    void aDisabledItemIsRefused() {
        assertEquals(Verdict.DISABLED_ITEM, check(true, true, false, 1, 64, false));
    }

    @Test
    void theOrderIsCreativeThenMenuThenCountThenItem() {
        // everything wrong at once: the first reason wins
        assertEquals(Verdict.NOT_CREATIVE, check(false, false, false, 999, 64, false));
        assertEquals(Verdict.OTHER_MENU, check(true, false, false, 999, 64, false));
        assertEquals(Verdict.BAD_COUNT, check(true, true, false, 999, 64, false));
    }
}
