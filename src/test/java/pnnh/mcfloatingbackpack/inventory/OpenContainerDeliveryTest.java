package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.OpenContainerDelivery.Choice;
import pnnh.mcfloatingbackpack.inventory.OpenContainerDelivery.Result;
import pnnh.mcfloatingbackpack.inventory.OpenContainerDelivery.SlotView;
import pnnh.mcfloatingbackpack.inventory.QuickTransferLogicTest.FakeStack;

/**
 * Runs the real {@link OpenContainerDelivery} against fake menus that behave like the ones the
 * feature has to live with: a chest (moves into the container area), a crafting table (container
 * area first, otherwise shuffles inside the player area), a menu that refuses, one that throws, and
 * a buggy one that never converges.
 */
class OpenContainerDeliveryTest {
    enum Mode {
        CHEST,
        CRAFTING,
        REFUSE,
        THROW_AFTER_PARTIAL,
        NEVER_CONVERGES
    }

    static final class FakeSlot {
        final boolean player;
        final int containerSlot; // -1 for non-player slots
        boolean selected;
        boolean mayPickup = true;
        Predicate<FakeStack> mayPlace = s -> true;
        FakeStack stack = FakeStack.EMPTY;

        FakeSlot(boolean player, int containerSlot) {
            this.player = player;
            this.containerSlot = containerSlot;
        }
    }

    /** Slots 0..containerSize-1 are the external container, then 36 player slots (hotbar 0-8, main 9-35). */
    static final class FakeMenu implements OpenContainerDelivery.MenuAccess<FakeStack> {
        final List<FakeSlot> slots = new ArrayList<>();
        final Mode mode;
        int quickMoveCalls;

        FakeMenu(Mode mode, int containerSize) {
            this.mode = mode;
            for (int i = 0; i < containerSize; i++) {
                slots.add(new FakeSlot(false, -1));
            }
            for (int i = 0; i < 36; i++) {
                slots.add(new FakeSlot(true, i));
            }
        }

        FakeSlot player(int containerSlot) {
            for (FakeSlot s : slots) {
                if (s.player && s.containerSlot == containerSlot) {
                    return s;
                }
            }
            throw new IllegalArgumentException();
        }

        void fillPlayerSlots(FakeStack stack) {
            for (FakeSlot s : slots) {
                if (s.player) {
                    s.stack = stack;
                }
            }
        }

        @Override
        public List<SlotView> slots(FakeStack source) {
            List<SlotView> views = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                FakeSlot s = slots.get(i);
                views.add(new SlotView(
                        i, s.player, s.containerSlot, !QuickTransferLogicTest.OPS.isEmpty(s.stack),
                        s.mayPickup, s.mayPlace.test(source), s.selected));
            }
            return views;
        }

        @Override
        public FakeStack get(int menuIndex) {
            return slots.get(menuIndex).stack;
        }

        @Override
        public void set(int menuIndex, FakeStack stack) {
            slots.get(menuIndex).stack = stack;
        }

        private int[] indices(boolean player, int except) {
            return java.util.stream.IntStream.range(0, slots.size())
                    .filter(i -> slots.get(i).player == player && i != except)
                    .toArray();
        }

        /** Moves as much of slot {@code from} as fits into the given slots; @return how many moved. */
        private int moveInto(int from, int[] targets) {
            FakeStack source = slots.get(from).stack;
            int before = source.count();
            FakeStack rest = QuickTransferLogic.moveInto(
                    QuickTransferLogicTest.OPS, source, targets, i -> slots.get(i).stack, (s, i) -> slots.get(i).stack = s);
            slots.get(from).stack = rest;
            return before - rest.count();
        }

        @Override
        public FakeStack quickMove(int menuIndex) {
            quickMoveCalls++;
            FakeStack original = slots.get(menuIndex).stack;
            if (QuickTransferLogicTest.OPS.isEmpty(original)) {
                return FakeStack.EMPTY;
            }
            switch (mode) {
                case REFUSE:
                    return FakeStack.EMPTY;
                case NEVER_CONVERGES:
                    return original;
                case THROW_AFTER_PARTIAL: {
                    moveInto(menuIndex, indices(false, -1));
                    throw new IllegalStateException("mod menu blew up");
                }
                case CRAFTING: {
                    int moved = moveInto(menuIndex, indices(false, -1));
                    if (moved == 0) {
                        moved = moveInto(menuIndex, indices(true, menuIndex));
                    }
                    return moved > 0 ? original : FakeStack.EMPTY;
                }
                default: {
                    int moved = moveInto(menuIndex, indices(false, -1));
                    return moved > 0 ? original : FakeStack.EMPTY;
                }
            }
        }

        int total(int kind) {
            int n = 0;
            for (FakeSlot s : slots) {
                if (!QuickTransferLogicTest.OPS.isEmpty(s.stack) && s.stack.kind() == kind) {
                    n += s.stack.count();
                }
            }
            return n;
        }
    }

    private static Result<FakeStack> deliver(FakeMenu menu, FakeStack source) {
        return OpenContainerDelivery.deliver(QuickTransferLogicTest.OPS, menu, source);
    }

    @Test
    void chestReceivesTheStackAndTheEmptyTempSlotIsLeftEmpty() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 6);
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 40, 64));
        assertTrue(QuickTransferLogicTest.OPS.isEmpty(r.leftover()));
        assertTrue(r.conserved());
        assertNull(r.error());
        assertEquals(40, menu.total(1));
        for (int i = 6; i < menu.slots.size(); i++) {
            assertTrue(QuickTransferLogicTest.OPS.isEmpty(menu.slots.get(i).stack), "player slot " + i + " must be empty again");
        }
    }

    @Test
    void chestMergesIntoExistingStacksFirst() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 3);
        menu.slots.get(2).stack = new FakeStack(1, 40, 64);
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 20, 64));
        assertTrue(QuickTransferLogicTest.OPS.isEmpty(r.leftover()));
        assertEquals(60, menu.slots.get(2).stack.count());
    }

    @Test
    void fullContainerLeavesTheStackInTheBackpackNotInThePlayerInventory() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 2);
        menu.slots.get(0).stack = new FakeStack(2, 64, 64);
        menu.slots.get(1).stack = new FakeStack(2, 64, 64);
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 10, 64));
        assertEquals(10, r.leftover().count());
        assertEquals(10, r.leftover().count());
        assertEquals(0, menu.total(1), "nothing may stay behind in a player slot");
        assertTrue(r.conserved());
    }

    @Test
    void partialFitReturnsTheRemainder() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 1);
        menu.slots.get(0).stack = new FakeStack(1, 60, 64);
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 10, 64));
        assertEquals(6, r.leftover().count());
        assertEquals(64, menu.slots.get(0).stack.count());
        assertEquals(64, menu.total(1));
        assertTrue(r.conserved());
    }

    @Test
    void refusingMenuLeavesEverythingInTheBackpack() {
        FakeMenu menu = new FakeMenu(Mode.REFUSE, 4);
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 10, 64));
        assertEquals(10, r.leftover().count());
        assertEquals(0, menu.total(1));
        assertTrue(r.conserved());
        assertEquals(1, menu.quickMoveCalls);
    }

    @Test
    void emptyPlayerSlotIsPreferredAndNothingIsDisplaced() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 3);
        FakeStack keep = new FakeStack(7, 5, 64);
        for (int i = 0; i < 36; i++) {
            if (i != 20) {
                menu.player(i).stack = keep;
            }
        }
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 8, 64));
        assertFalse(r.swapped());
        assertEquals(8, menu.total(1));
        assertSame(keep, menu.player(0).stack);
    }

    @Test
    void fullInventoryDisplacesAMainSlotAndRestoresTheSameObject() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 3);
        FakeStack x = new FakeStack(7, 5, 64);
        menu.fillPlayerSlots(x);
        FakeStack other = new FakeStack(8, 2, 64);
        menu.player(9).stack = other; // lowest eligible main slot
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 8, 64));
        assertTrue(r.swapped());
        assertTrue(QuickTransferLogicTest.OPS.isEmpty(r.leftover()));
        assertEquals(8, menu.total(1));
        assertSame(other, menu.player(9).stack, "the displaced stack must come back as the very same object");
        assertTrue(r.conserved());
    }

    @Test
    void neverUsesTheHeldHotbarSlotOrSlotsThatCannotBePickedUpOrPlaced() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 3);
        FakeStack x = new FakeStack(7, 5, 64);
        menu.fillPlayerSlots(x);
        menu.player(3).selected = true;
        // every main slot except #30 is unusable for different reasons
        for (int i = 9; i < 36; i++) {
            if (i < 20) {
                menu.player(i).mayPickup = false;
            } else if (i < 30) {
                menu.player(i).mayPlace = s -> false;
            }
        }
        // hotbar (0-8, incl. the held one) is never a displacement candidate
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 8, 64));
        assertTrue(r.swapped());
        assertEquals(8, menu.total(1));
        assertSame(x, menu.player(3).stack);
        assertSame(x, menu.player(30).stack);
    }

    @Test
    void noUsableSlotMeansNoTransferAtAll() {
        FakeMenu menu = new FakeMenu(Mode.CHEST, 3);
        FakeStack x = new FakeStack(7, 5, 64);
        menu.fillPlayerSlots(x);
        for (int i = 9; i < 36; i++) {
            menu.player(i).mayPickup = false;
        }
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 8, 64));
        assertTrue(r.skipped());
        assertEquals(8, r.leftover().count());
        assertEquals(0, menu.quickMoveCalls);
        assertEquals(0, menu.total(1));
    }

    @Test
    void craftingStyleMenuMayShuffleInsidePlayerAreaAndTotalsStillHold() {
        FakeMenu menu = new FakeMenu(Mode.CRAFTING, 1);
        menu.slots.get(0).stack = new FakeStack(9, 1, 1); // the single grid slot is taken
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 8, 64));
        assertTrue(QuickTransferLogicTest.OPS.isEmpty(r.leftover()));
        assertEquals(8, menu.total(1), "the items now sit in other player slots");
        assertTrue(r.conserved());
    }

    @Test
    void throwingMenuDoesNotLeakTheExceptionAndTheTempSlotContentIsRecovered() {
        FakeMenu menu = new FakeMenu(Mode.THROW_AFTER_PARTIAL, 1);
        FakeStack x = new FakeStack(7, 5, 64);
        menu.fillPlayerSlots(x);
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 64, 64));
        assertNotNull(r.error());
        assertTrue(r.swapped());
        assertSame(x, menu.player(9).stack);
        // one slot in the container took its share before the menu blew up; the rest must be back in the backpack
        assertEquals(64, menu.total(1) + r.leftover().count());
        assertTrue(r.conserved());
    }

    @Test
    void menuThatNeverConvergesIsCutOffAtTheLimit() {
        FakeMenu menu = new FakeMenu(Mode.NEVER_CONVERGES, 3);
        Result<FakeStack> r = deliver(menu, new FakeStack(1, 5, 64));
        assertEquals(5, r.leftover().count());
        assertTrue(menu.quickMoveCalls <= 5 + 4 + 1, "calls: " + menu.quickMoveCalls);
        assertTrue(menu.quickMoveCalls > 1);
        assertTrue(r.conserved());
    }

    @Test
    void tempSlotSelectionIsDeterministicLowestIndexWins() {
        List<SlotView> views = new ArrayList<>();
        views.add(new SlotView(0, false, -1, false, true, true, false));
        views.add(new SlotView(5, true, 12, false, true, true, false));
        views.add(new SlotView(3, true, 4, false, true, true, false));
        Optional<Choice> c = OpenContainerDelivery.selectTempSlot(views);
        assertTrue(c.isPresent());
        assertEquals(3, c.get().menuIndex());
        assertFalse(c.get().displace());
        assertEquals(c, OpenContainerDelivery.selectTempSlot(views));
    }

    @Test
    void tempSlotRejectsArmorOffhandAndNonPlayerSlots() {
        List<SlotView> views = new ArrayList<>();
        views.add(new SlotView(0, false, -1, false, true, true, false)); // chest cell
        views.add(new SlotView(1, true, 36, false, true, true, false)); // armor slot of the player inventory
        views.add(new SlotView(2, true, 40, false, true, true, false)); // offhand
        assertTrue(OpenContainerDelivery.selectTempSlot(views).isEmpty());
    }

    @Test
    void openContainerDetectionLooksForSlotsOutsideThePlayerInventory() {
        List<SlotView> onlyPlayer = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            onlyPlayer.add(new SlotView(i, true, i, false, true, true, false));
        }
        assertFalse(OpenContainerDelivery.hasOpenContainer(onlyPlayer));
        onlyPlayer.add(new SlotView(41, false, -1, false, true, true, false));
        assertTrue(OpenContainerDelivery.hasOpenContainer(onlyPlayer));
    }

    @Test
    void totalsAreConservedOnRandomLayoutsAcrossModes() {
        Random random = new Random(20261105L);
        Mode[] modes = {Mode.CHEST, Mode.CRAFTING, Mode.REFUSE, Mode.THROW_AFTER_PARTIAL};
        int[] max = {1, 16, 64};
        for (int round = 0; round < 100; round++) {
            FakeMenu menu = new FakeMenu(modes[random.nextInt(modes.length)], 1 + random.nextInt(8));
            for (FakeSlot s : menu.slots) {
                if (random.nextInt(3) > 0) {
                    int kind = 1 + random.nextInt(3);
                    int m = max[kind - 1];
                    s.stack = new FakeStack(kind, 1 + random.nextInt(m), m);
                }
            }
            if (random.nextInt(4) == 0) {
                menu.player(random.nextInt(9)).selected = true;
            }
            int kind = 1 + random.nextInt(3);
            int m = max[kind - 1];
            FakeStack source = new FakeStack(kind, 1 + random.nextInt(m), m);

            int before = menu.total(kind) + source.count();
            Result<FakeStack> r = deliver(menu, source);
            int after = menu.total(kind) + r.leftover().count();

            assertEquals(before, after, "round " + round + " mode " + menu.mode + ": total changed");
            for (FakeSlot s : menu.slots) {
                assertTrue(s.stack.count() <= s.stack.max() || QuickTransferLogicTest.OPS.isEmpty(s.stack), "round " + round);
            }
        }
    }
}
