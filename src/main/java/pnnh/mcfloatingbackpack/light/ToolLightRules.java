package pnnh.mcfloatingbackpack.light;

import java.util.List;
import java.util.function.Predicate;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * 052: whether a light item may be put into a slot of a tool area. A light only counts in the bottom area, and there at most one
 * (whatever its kind); the other areas and the main area take anything. A backpack that already holds more than one (saved before
 * the limit existed) keeps them but cannot get another; replacing a light with another one never adds one. The rule is independent
 * of the one tag manager the bottom area is limited to. Pure logic over an abstract stack type, so the server and the client's
 * prediction ask the same function and it can be unit-tested.
 */
public final class ToolLightRules {
    public enum Verdict { OK, DUPLICATE }

    private ToolLightRules() {
    }

    /**
     * @param area      where the stack would go
     * @param incoming  the stack put in; one that is not a light is always fine
     * @param slotIndex the slot of {@code area} it goes into (the stack there leaves it)
     * @param areaSlots the whole stored list of the area
     * @param isLight   whether a stack is a light item
     */
    public static <T> Verdict check(PanelArea area, T incoming, int slotIndex, List<T> areaSlots, Predicate<T> isLight) {
        if (area != PanelArea.BOTTOM || !isLight.test(incoming)) {
            return Verdict.OK;
        }
        int before = 0;
        for (T stack : areaSlots) {
            if (isLight.test(stack)) {
                before++;
            }
        }
        boolean replacesOne = slotIndex >= 0 && slotIndex < areaSlots.size() && isLight.test(areaSlots.get(slotIndex));
        int after = before - (replacesOne ? 1 : 0) + 1;
        return after > 1 && after > before ? Verdict.DUPLICATE : Verdict.OK;
    }

    /** The index of the first light in the list (the one that counts), or -1. */
    public static <T> int firstLight(List<T> areaSlots, Predicate<T> isLight) {
        for (int i = 0; i < areaSlots.size(); i++) {
            if (isLight.test(areaSlots.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
