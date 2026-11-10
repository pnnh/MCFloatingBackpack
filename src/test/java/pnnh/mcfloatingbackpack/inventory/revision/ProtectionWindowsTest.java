package pnnh.mcfloatingbackpack.inventory.revision;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProtectionWindowsTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final ProtectionWindows windows = new ProtectionWindows();

    @Test
    void noWindowUntilOneIsOpened() {
        assertFalse(windows.isOpen(ALICE, "pack1", 0));
    }

    @Test
    void aWindowIsOpenForSixtySecondsAndNotAfter() {
        windows.open(ALICE, "pack1", 1_000);
        assertTrue(windows.isOpen(ALICE, "pack1", 1_000));
        assertTrue(windows.isOpen(ALICE, "pack1", 60_999));
        assertFalse(windows.isOpen(ALICE, "pack1", 61_000));
        assertFalse(windows.isOpen(ALICE, "pack1", 500_000));
    }

    @Test
    void usingTheWindowEndsIt() {
        windows.open(ALICE, "pack1", 0);
        windows.consume(ALICE, "pack1");
        assertFalse(windows.isOpen(ALICE, "pack1", 10));
    }

    @Test
    void aWindowIsOnlyGoodForItsOwnPlayerAndBackpack() {
        windows.open(ALICE, "pack1", 0);
        assertFalse(windows.isOpen(BOB, "pack1", 10)); // another player cannot borrow it
        assertFalse(windows.isOpen(ALICE, "pack2", 10)); // nor can another backpack
        assertTrue(windows.isOpen(ALICE, "pack1", 10));
    }

    @Test
    void twoBackpacksOfOnePlayerHaveWindowsOfTheirOwn() {
        windows.open(ALICE, "pack1", 0);
        windows.open(ALICE, "pack2", 30_000);
        windows.consume(ALICE, "pack1");
        assertFalse(windows.isOpen(ALICE, "pack1", 40_000));
        assertTrue(windows.isOpen(ALICE, "pack2", 40_000));
    }

    @Test
    void openingAgainRestartsTheWindow() {
        windows.open(ALICE, "pack1", 0);
        windows.open(ALICE, "pack1", 50_000);
        assertTrue(windows.isOpen(ALICE, "pack1", 100_000));
        assertFalse(windows.isOpen(ALICE, "pack1", 110_000));
    }

    @Test
    void aPlayerWhoLeavesTakesEveryWindowWithThem() {
        windows.open(ALICE, "pack1", 0);
        windows.open(ALICE, "pack2", 0);
        windows.open(BOB, "pack1", 0);
        windows.forgetPlayer(ALICE);
        assertFalse(windows.isOpen(ALICE, "pack1", 1));
        assertFalse(windows.isOpen(ALICE, "pack2", 1));
        assertTrue(windows.isOpen(BOB, "pack1", 1));
    }

    @Test
    void theLogLineForASkippedPickupComesAtMostOnceAMinuteForEachPlayer() {
        assertTrue(windows.shouldLogPickupSkip(ALICE, 0));
        assertFalse(windows.shouldLogPickupSkip(ALICE, 10_000));
        assertFalse(windows.shouldLogPickupSkip(ALICE, 59_999));
        assertTrue(windows.shouldLogPickupSkip(ALICE, 60_000));
        assertTrue(windows.shouldLogPickupSkip(BOB, 10_000)); // another player has their own
        windows.forgetPlayer(BOB);
        assertTrue(windows.shouldLogPickupSkip(BOB, 10_001));
    }
}
