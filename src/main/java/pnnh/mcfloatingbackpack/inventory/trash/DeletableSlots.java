package pnnh.mcfloatingbackpack.inventory.trash;

/**
 * 022: which slots of the vanilla player-inventory and chest screens the delete key may take. In the player's own
 * inventory that is the main part (container slots 9..35), never the hotbar, armor or offhand; in a chest it is the
 * chest's own slots. Judged by the slot's container and its slot number inside it, not by the menu slot number, so a
 * chest of any size works. Pure logic, shared by the client (what to offer) and the server (what to accept).
 */
public final class DeletableSlots {
    public static final int OWN_FIRST = 9;
    public static final int OWN_LAST = 35;

    private DeletableSlots() {
    }

    public static boolean allowed(boolean ownInventory, int containerSlot, boolean chestOwnSlot) {
        if (ownInventory) {
            return containerSlot >= OWN_FIRST && containerSlot <= OWN_LAST;
        }
        return chestOwnSlot;
    }
}
