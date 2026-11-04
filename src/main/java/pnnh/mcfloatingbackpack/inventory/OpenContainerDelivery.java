package pnnh.mcfloatingbackpack.inventory;

import java.util.List;
import java.util.Optional;

/**
 * Puts one stack into whatever container menu the player has open, using the menu's <em>own</em>
 * rule for "a stack in a player-inventory slot was shift-clicked" (012). The stack is parked in a
 * player-inventory slot, the menu's {@code quickMoveStack} is called on that slot, and whatever did
 * not move is taken back.
 *
 * <p>Written over abstract stacks and menus so it has no Minecraft dependency: the unit tests drive
 * the real algorithm through fake menus (chest-like, crafting-like, refusing, throwing, never
 * converging); {@link ContainerMenuAccess} is the adapter for real menus.
 */
public final class OpenContainerDelivery {
    /** Defensive cap on {@code quickMoveStack} calls for one stack (vanilla's own loop has none). */
    static final int HARD_CALL_LIMIT = 4096;

    private OpenContainerDelivery() {
    }

    /**
     * Read-only description of one menu slot.
     *
     * @param belongsToPlayerInventory the slot's container is the player's {@code Inventory}
     *     (includes armor and offhand, which share it)
     * @param containerSlot index inside the player's {@code Inventory} (0-8 hotbar, 9-35 main), -1 otherwise
     * @param mayPlaceSource {@code slot.mayPlace(stack being delivered)}
     * @param isSelectedHotbar the slot the player is holding
     */
    public record SlotView(
            int menuIndex,
            boolean belongsToPlayerInventory,
            int containerSlot,
            boolean hasItem,
            boolean mayPickup,
            boolean mayPlaceSource,
            boolean isSelectedHotbar) {
        /** Main inventory or hotbar: the only player slots a stack may be parked in. */
        boolean isTransferablePlayerSlot() {
            return belongsToPlayerInventory && containerSlot >= 0 && containerSlot < QuickTransferRequest.INVENTORY_SLOTS;
        }
    }

    public interface MenuAccess<T> {
        /** All slots of the open menu; {@code source} is the stack about to be delivered. */
        List<SlotView> slots(T source);

        T get(int menuIndex);

        void set(int menuIndex, T stack);

        /** The menu's {@code quickMoveStack} for this slot; empty when nothing moved. */
        T quickMove(int menuIndex);
    }

    public record Choice(int menuIndex, boolean displace) {
    }

    /**
     * @param leftover what did not go into the container (to be put back in the backpack)
     * @param conserved the item total over all menu slots plus the stack was the same before and after
     * @param error exception thrown by the menu, if any (swallowed; the temp slot was still cleaned up)
     * @param skipped no parking slot was usable, nothing was tried
     * @param swapped an occupied slot had to be emptied for the duration of the call
     */
    public record Result<T>(T leftover, boolean conserved, RuntimeException error, boolean skipped, boolean swapped) {
    }

    /** A menu has an "open container" when it has slots that are not the player's inventory. */
    public static boolean hasOpenContainer(List<SlotView> slots) {
        for (SlotView slot : slots) {
            if (!slot.belongsToPlayerInventory()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where to park the stack. First an empty player slot; with a full inventory, an occupied
     * <em>main</em>-inventory slot that can be picked up and accepts the stack, never the held
     * hotbar slot (mods protect the held backpack and the slot the opened storage lives in). Lowest
     * menu index wins so the choice is deterministic.
     */
    public static Optional<Choice> selectTempSlot(List<SlotView> slots) {
        SlotView best = null;
        for (SlotView slot : slots) {
            if (slot.isTransferablePlayerSlot() && !slot.hasItem() && slot.mayPlaceSource()
                    && (best == null || slot.menuIndex() < best.menuIndex())) {
                best = slot;
            }
        }
        if (best != null) {
            return Optional.of(new Choice(best.menuIndex(), false));
        }
        for (SlotView slot : slots) {
            if (slot.isTransferablePlayerSlot()
                    && slot.hasItem()
                    && slot.containerSlot() >= 9
                    && slot.mayPickup()
                    && slot.mayPlaceSource()
                    && !slot.isSelectedHotbar()
                    && (best == null || slot.menuIndex() < best.menuIndex())) {
                best = slot;
            }
        }
        return best == null ? Optional.empty() : Optional.of(new Choice(best.menuIndex(), true));
    }

    public static <T> Result<T> deliver(QuickTransferLogic.StackOps<T> ops, MenuAccess<T> menu, T source) {
        if (ops.isEmpty(source)) {
            return new Result<>(ops.empty(), true, null, true, false);
        }
        List<SlotView> views = menu.slots(source);
        Optional<Choice> choice = selectTempSlot(views);
        if (choice.isEmpty()) {
            return new Result<>(source, true, null, true, false);
        }
        int index = choice.get().menuIndex();
        boolean swapped = choice.get().displace();

        int before = totalOf(ops, menu, views, source) + ops.count(source);

        T displaced = swapped ? menu.get(index) : null;
        menu.set(index, ops.withCount(source, ops.count(source)));

        RuntimeException error = null;
        T leftover = ops.empty();
        try {
            // Same loop as vanilla's doClick: keep going until the menu reports that nothing moved.
            int limit = Math.min(ops.count(source) + 4, HARD_CALL_LIMIT);
            T result = menu.quickMove(index);
            for (int i = 0; i < limit && !ops.isEmpty(result) && ops.sameKind(menu.get(index), result); i++) {
                result = menu.quickMove(index);
            }
        } catch (RuntimeException e) {
            error = e;
        } finally {
            T left = menu.get(index);
            if (!ops.isEmpty(left) && ops.sameKind(left, source)) {
                leftover = ops.withCount(left, ops.count(left));
            }
            menu.set(index, swapped ? displaced : ops.empty());
        }

        int after = totalOf(ops, menu, views, source) + ops.count(leftover);
        return new Result<>(leftover, before == after, error, false, swapped);
    }

    private static <T> int totalOf(QuickTransferLogic.StackOps<T> ops, MenuAccess<T> menu, List<SlotView> views, T kind) {
        int total = 0;
        for (SlotView view : views) {
            T stack = menu.get(view.menuIndex());
            if (!ops.isEmpty(stack) && ops.sameKind(stack, kind)) {
                total += ops.count(stack);
            }
        }
        return total;
    }
}
