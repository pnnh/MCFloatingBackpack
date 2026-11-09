package pnnh.mcfloatingbackpack.inventory.creative;

import net.minecraft.server.level.ServerPlayer;

/**
 * 029: after the server moves items in or out of a creative player's inventory (the Ctrl quick transfer), it sends the whole
 * inventory to that player once. The creative inventory screen's item tab ignores single-slot updates for every slot but the
 * hotbar (the game's own rule), so without this the client would keep showing, and later write back, the old contents of the
 * slots that were changed. The whole-inventory packet is never ignored.
 *
 * <p>Only the server's main thread calls this.
 */
public final class CreativeInventorySync {
    private CreativeInventorySync() {
    }

    /** Pure decision: only a creative player with their own inventory menu open (the creative inventory screen) needs it. */
    public static boolean shouldSync(boolean creative, boolean inventoryMenuOpen) {
        return creative && inventoryMenuOpen;
    }

    /** Sends the whole inventory (and the cursor, which is empty during a quick transfer) if the player needs it. */
    public static void afterInventoryChange(ServerPlayer player) {
        if (shouldSync(player.isCreative(), player.containerMenu == player.inventoryMenu)) {
            player.inventoryMenu.sendAllDataToRemote();
        }
    }
}
