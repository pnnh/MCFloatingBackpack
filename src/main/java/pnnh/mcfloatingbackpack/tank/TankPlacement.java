package pnnh.mcfloatingbackpack.tank;

import java.util.List;
import java.util.function.Function;

import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.tank.TankCatalog.Tank;

/**
 * 048: whether a tank may be put into a slot of a tool area. The tier rule applies to all three tool areas (a netherite tank does
 * not go into a leather backpack's side area either); a tank only counts in the bottom area, so only there is it one per liquid
 * and must the liquid already stored fit it. The main area is ordinary storage and takes anything. The server asks; the client
 * asks the same function to spare a request that is sure to fail. Pure logic over an abstract stack type.
 */
public final class TankPlacement {
    public enum Verdict { OK, TIER_TOO_HIGH, DUPLICATE_KIND, STORE_EXCEEDS_CAPACITY }

    private TankPlacement() {
    }

    /**
     * @param area       where the stack would go
     * @param incoming   the stack put in; one that is not a tank is always fine
     * @param slotIndex  the slot of {@code area} it goes into (the stack there leaves it)
     * @param areaSlots  the whole stored list of the area
     * @param tankOf     what tank a stack is, {@code null} for any other stack
     * @param backpack   the tier of the backpack, {@code null} if it is not known
     * @param storedMb   how much of the incoming tank's liquid the backpack holds now
     */
    public static <T> Verdict check(
            PanelArea area,
            T incoming,
            int slotIndex,
            List<T> areaSlots,
            Function<T, Tank> tankOf,
            BackpackTier backpack,
            int storedMb) {
        Tank tank = area == PanelArea.MAIN ? null : tankOf.apply(incoming);
        if (tank == null) {
            return Verdict.OK;
        }
        if (!TankCatalog.allowedIn(tank.tier(), backpack)) {
            return Verdict.TIER_TOO_HIGH;
        }
        if (area != PanelArea.BOTTOM) {
            return Verdict.OK;
        }
        int before = 0;
        for (T stack : areaSlots) {
            Tank other = tankOf.apply(stack);
            if (other != null && other.kind() == tank.kind()) {
                before++;
            }
        }
        Tank replaced = slotIndex >= 0 && slotIndex < areaSlots.size() ? tankOf.apply(areaSlots.get(slotIndex)) : null;
        boolean replacesOne = replaced != null && replaced.kind() == tank.kind();
        int after = before - (replacesOne ? 1 : 0) + 1;
        if (after > 1 && after > before) {
            return Verdict.DUPLICATE_KIND;
        }
        if (storedMb > TankCatalog.capacityMb(tank.tier())) {
            return Verdict.STORE_EXCEEDS_CAPACITY;
        }
        return Verdict.OK;
    }
}
