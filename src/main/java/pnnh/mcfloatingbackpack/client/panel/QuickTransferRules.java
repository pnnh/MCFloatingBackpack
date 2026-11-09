package pnnh.mcfloatingbackpack.client.panel;

/**
 * Plain decisions for the Ctrl quick-transfer gesture, kept free of Minecraft types so they can
 * be unit-tested.
 */
public final class QuickTransferRules {
    private QuickTransferRules() {
    }

    /**
     * Any container screen, vanilla or a mod's (Sophisticated Backpacks, Traveler's Backpack: 012 needs
     * their container slots as a source). The creative inventory is one of them since 029: its inventory-tab slots are numbered
     * by menu (see {@link #creativeInventoryIndex}), which the caller translates.
     */
    public static boolean isInterceptableScreenClass(String className) {
        return className != null && !className.isEmpty();
    }

    /**
     * The real index in the player's inventory of a slot of the creative inventory screen, or -1 if it is not one that can
     * be transferred (armor, the crafting grid, the offhand, anything outside 0-35).
     *
     * <p>On the <em>inventory</em> tab the screen wraps the slots of the player's inventory menu and numbers each wrapper by its
     * <em>menu</em> number (the hotbar is 36-44 there, but 0-8 in the inventory), so the number has to be translated. On the
     * <em>item</em> tab the hotbar is made of real slots whose number is the inventory index.
     *
     * @param inventoryTab the creative screen shows its inventory tab
     * @param containerSlot what {@code Slot.getContainerSlot()} says
     * @param menuSlotToInventoryIndex the player's inventory menu: menu number to inventory index, or -1 if that slot is not in
     *     the player's inventory
     */
    public static int creativeInventoryIndex(
            boolean inventoryTab, int containerSlot, java.util.function.IntUnaryOperator menuSlotToInventoryIndex) {
        int index = inventoryTab ? menuSlotToInventoryIndex.applyAsInt(containerSlot) : containerSlot;
        return isTransferableInventorySlot(true, index, true) ? index : -1;
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
