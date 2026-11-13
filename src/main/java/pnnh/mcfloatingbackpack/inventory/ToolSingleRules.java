package pnnh.mcfloatingbackpack.inventory;

import java.util.List;
import java.util.function.Predicate;

/**
 * 053: whether a tool of some kind (the mobile jukebox, later the mobile crafting table and furnace) may be put into a slot of a tool
 * area: in the one area where it counts there is at most one of it, in every other area it is just an item. A backpack that already holds
 * more than one (saved before the limit existed) keeps them but cannot get another; replacing one with another of the same kind never adds
 * one. It is independent of the one trash can, tag manager, auto pickup and light that {@link ToolAreaRules} and the light rule limit.
 * Pure logic over an abstract stack type, so the server and the client's prediction ask the same function and it can be unit-tested.
 */
public final class ToolSingleRules {
    public enum Verdict { OK, DUPLICATE }

    private ToolSingleRules() {
    }

    /**
     * @param targetArea the area where this kind of tool counts
     * @param area       where the stack would go
     * @param incoming   the stack put in; one that is not of the kind is always fine
     * @param slotIndex  the slot of {@code area} it goes into (the stack there leaves it)
     * @param areaSlots  the whole stored list of the area
     * @param isKind     whether a stack is of the kind
     */
    public static <T> Verdict check(PanelArea targetArea, PanelArea area, T incoming, int slotIndex, List<T> areaSlots, Predicate<T> isKind) {
        if (area != targetArea || !isKind.test(incoming)) {
            return Verdict.OK;
        }
        int before = 0;
        for (T stack : areaSlots) {
            if (isKind.test(stack)) {
                before++;
            }
        }
        boolean replacesOne = slotIndex >= 0 && slotIndex < areaSlots.size() && isKind.test(areaSlots.get(slotIndex));
        int after = before - (replacesOne ? 1 : 0) + 1;
        return after > 1 && after > before ? Verdict.DUPLICATE : Verdict.OK;
    }
}
