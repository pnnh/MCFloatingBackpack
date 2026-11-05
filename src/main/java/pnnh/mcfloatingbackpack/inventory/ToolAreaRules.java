package pnnh.mcfloatingbackpack.inventory;

import java.util.List;
import java.util.function.Predicate;

/**
 * What may be put into a tool area (016, FR-011); this is the one place the server asks. Since 021 two tools are
 * limited: the right area holds at most one trash can and the bottom (function) area at most one tag manager.
 * Anything else, in any area, is accepted. Pure logic over an abstract stack type, so it can be unit-tested.
 */
public final class ToolAreaRules {
    /** How many of the area's own tool fit in it at once. */
    public static final int LIMIT_PER_AREA = 1;

    private ToolAreaRules() {
    }

    /**
     * Whether {@code incoming} may be put into slot {@code slotIndex} of {@code area}, whose whole stored list is
     * {@code areaSlots}. The slot's current stack leaves it, so replacing a tool with the same kind of tool never
     * adds one. A placement is refused only if it leaves more than {@link #LIMIT_PER_AREA} of the tool in the area
     * <em>and</em> more than were there before; so a backpack that already holds more (saved before the limit
     * existed) keeps them, but cannot get another.
     */
    public static <T> boolean canPlace(
            PanelArea area,
            T incoming,
            int slotIndex,
            List<T> areaSlots,
            Predicate<T> isTrashCan,
            Predicate<T> isTagManager) {
        Predicate<T> limited = switch (area) {
            case RIGHT -> isTrashCan;
            case BOTTOM -> isTagManager;
            default -> null;
        };
        if (limited == null || !limited.test(incoming)) {
            return true;
        }
        int before = 0;
        for (T stack : areaSlots) {
            if (limited.test(stack)) {
                before++;
            }
        }
        boolean replacesOne = slotIndex >= 0 && slotIndex < areaSlots.size() && limited.test(areaSlots.get(slotIndex));
        int after = before - (replacesOne ? 1 : 0) + 1;
        return after <= LIMIT_PER_AREA || after <= before;
    }
}
