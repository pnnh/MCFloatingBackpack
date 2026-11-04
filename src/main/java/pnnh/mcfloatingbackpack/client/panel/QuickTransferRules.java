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
     * The gesture only acts on vanilla container screens. Third-party screens (Sophisticated
     * Backpacks, Traveler's Backpack, ...) also bind their player-inventory slots to the real
     * {@code Inventory}, so slot checks alone cannot tell them apart; the package does. The creative
     * inventory uses client-side fake slots whose numbering does not match the server.
     */
    public static boolean isSupportedScreenClass(String className) {
        return className != null && className.startsWith("net.minecraft.") && !className.equals(CREATIVE_SCREEN);
    }

    /**
     * Main inventory plus hotbar only: container slots 0-35 of the player's own inventory.
     * Armor (36-39) and offhand (40) share that container but are excluded; chest cells and the
     * crafting grid have a different container.
     */
    public static boolean isTransferableInventorySlot(boolean sameInventory, int containerSlot, boolean active) {
        return sameInventory && active && containerSlot >= 0 && containerSlot < 36;
    }
}
