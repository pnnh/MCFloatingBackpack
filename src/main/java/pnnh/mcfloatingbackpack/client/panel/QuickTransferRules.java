package pnnh.mcfloatingbackpack.client.panel;

/**
 * Plain decisions for the Ctrl quick-transfer gesture, kept free of Minecraft types so they can
 * be unit-tested.
 */
public final class QuickTransferRules {
    private static final String CREATIVE_SCREEN =
            "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen";

    private QuickTransferRules() {
    }

    /**
     * Any container screen, vanilla or a mod's (Sophisticated Backpacks, Traveler's Backpack: 012 needs
     * their container slots as a source). The creative inventory uses client-side fake slots whose
     * numbering does not match the server, so it is excluded.
     */
    public static boolean isInterceptableScreenClass(String className) {
        return className != null && !className.isEmpty() && !className.equals(CREATIVE_SCREEN);
    }

    /**
     * Main inventory plus hotbar only: container slots 0-35 of the player's own inventory.
     * Armor (36-39) and offhand (40) share that container but are excluded; chest cells and the
     * crafting grid have a different container.
     */
    public static boolean isTransferableInventorySlot(boolean sameInventory, int containerSlot, boolean active) {
        return sameInventory && active && containerSlot >= 0 && containerSlot < 36;
    }

    /**
     * A slot of the opened container that can be a source for "into the backpack": visible, can be
     * picked up, and can take its own item back (vanilla's {@code Slot.allowModification}). Output-style
     * slots (crafting result, furnace output, anvil, trades) refuse every stack, so they are left to the game.
     */
    public static boolean isContainerSourceSlot(boolean sameInventory, boolean active, boolean mayPickup, boolean mayPlaceBack) {
        return !sameInventory && active && mayPickup && mayPlaceBack;
    }
}
