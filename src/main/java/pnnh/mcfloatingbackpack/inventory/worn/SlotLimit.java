package pnnh.mcfloatingbackpack.inventory.worn;

/**
 * 061: the rule "one floating backpack of any tier in each slot type" of the accessory mods (Curios, Accessories): a slot type may have
 * several slots, but only one of them may hold a floating backpack, because the panel key and the back layer only ever look at the first
 * one. The two mods ask "may this stack go into this slot?" when a stack is put in; the answer comes from here. Pure logic, so it is
 * tested without either mod.
 *
 * <p>Only putting in is judged: taking out, swapping places and what commands or creative mode put in are not, and nothing already in a
 * slot is ever moved or removed.
 */
public final class SlotLimit {
    private SlotLimit() {
    }

    /**
     * @param othersInType how many floating backpacks the entity already has in the real (not cosmetic) slots of this slot type, not
     *     counting the slot the stack is going into
     * @param enforced whether this mod's compatibility is at FULL (at any other level this mod's adaptation is off and nothing is limited)
     */
    public static boolean allows(int othersInType, boolean enforced) {
        return !enforced || othersInType <= 0;
    }
}
