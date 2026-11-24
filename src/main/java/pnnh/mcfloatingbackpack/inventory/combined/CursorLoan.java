package pnnh.mcfloatingbackpack.inventory.combined;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;

/**
 * 066: the "all" tab's in-memory cursor lent to one member's inventory for one request. The panel's click logic works on the cursor of the
 * backpack it is given; the loan puts the session's cursor there for the request and, before the backpack is saved, takes it back and clears it,
 * so what is written into the backpack never holds the cursor (the contents of a combined backpack's members are never touched by it).
 */
public final class CursorLoan {
    private final AllState all;

    private CursorLoan(AllState all) {
        this.all = all;
    }

    public static CursorLoan borrow(AllState all, BackpackInventory inventory) {
        inventory.setCursor(all.cursor());
        return new CursorLoan(all);
    }

    /** Takes the cursor back from the inventory (what the request left on it) and leaves the inventory without one. Call before each save. */
    public void settle(BackpackInventory inventory) {
        all.setCursor(inventory.getCursor());
        inventory.setCursor(ItemStack.EMPTY);
    }
}
