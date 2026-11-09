package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickTransferRulesTest {
    @Test
    void anyContainerScreenIsInterceptableTheCreativeInventoryIncluded() {
        assertTrue(QuickTransferRules.isInterceptableScreenClass(
                "net.minecraft.client.gui.screens.inventory.InventoryScreen"));
        assertTrue(QuickTransferRules.isInterceptableScreenClass(
                "net.minecraft.client.gui.screens.inventory.ContainerScreen"));
        // 012 lifts 011's vanilla-only whitelist: the mods' screens are the whole point of the reverse direction
        assertTrue(QuickTransferRules.isInterceptableScreenClass(
                "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase"));
        assertTrue(QuickTransferRules.isInterceptableScreenClass(
                "com.tiviacz.travelersbackpack.client.screens.BackpackScreen"));
    }

    @Test
    void theCreativeInventoryIsInterceptableSince029() {
        // 029: the creative inventory is vanilla and the gesture works there (its slots are translated, see below)
        assertTrue(QuickTransferRules.isInterceptableScreenClass(
                "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen"));
    }

    @Test
    void emptyNamesAreNotInterceptable() {
        assertFalse(QuickTransferRules.isInterceptableScreenClass(null));
        assertFalse(QuickTransferRules.isInterceptableScreenClass(""));
    }

    @Test
    void onlyMainInventoryAndHotbarSlotsAreTransferable() {
        for (int i = 0; i < 36; i++) {
            assertTrue(QuickTransferRules.isTransferableInventorySlot(true, i, true), "slot " + i);
        }
        // armor 36-39 and offhand 40 share the player Inventory but are excluded
        for (int i = 36; i <= 40; i++) {
            assertFalse(QuickTransferRules.isTransferableInventorySlot(true, i, true), "slot " + i);
        }
        assertFalse(QuickTransferRules.isTransferableInventorySlot(false, 3, true));
        assertFalse(QuickTransferRules.isTransferableInventorySlot(true, 3, false));
        assertFalse(QuickTransferRules.isTransferableInventorySlot(true, -1, true));
    }

    @Test
    void containerSourceSlotsMustBeActiveAndAbleToTakeTheirItemBackIn() {
        // an ordinary storage cell
        assertTrue(QuickTransferRules.isContainerSourceSlot(false, true, true, true));
        // belongs to the player inventory: handled by the inventory rules instead
        assertFalse(QuickTransferRules.isContainerSourceSlot(true, true, true, true));
        // scrolled out of view / hidden
        assertFalse(QuickTransferRules.isContainerSourceSlot(false, false, true, true));
        // cannot be picked up
        assertFalse(QuickTransferRules.isContainerSourceSlot(false, true, false, true));
        // output-style slot: mayPlace is false for any stack, so the item could not be put back (furnace
        // output, crafting result, anvil result, trade result ...)
        assertFalse(QuickTransferRules.isContainerSourceSlot(false, true, true, false));
    }

    /**
     * What the player's inventory menu has at each menu number (InventoryMenu's constructor): 0 crafting result, 1-4 crafting
     * grid, 5-8 armor (inventory 39..36), 9-35 main inventory, 36-44 hotbar (inventory 0..8), 45 offhand (inventory 40).
     */
    private static int vanillaMenuToInventory(int menuSlot) {
        if (menuSlot >= 9 && menuSlot <= 35) {
            return menuSlot;
        }
        if (menuSlot >= 36 && menuSlot <= 44) {
            return menuSlot - 36;
        }
        if (menuSlot >= 5 && menuSlot <= 8) {
            return 39 - (menuSlot - 5);
        }
        if (menuSlot == 45) {
            return 40;
        }
        return -1; // the crafting result and grid are not in the player's inventory
    }

    @Test
    void onTheCreativeInventoryTabTheSlotNumberIsAMenuNumberAndIsTranslated() {
        // the hotbar is menu 36..44 but inventory 0..8 (the case that was wrong before 029)
        for (int i = 0; i < 9; i++) {
            assertEquals(i, QuickTransferRules.creativeInventoryIndex(true, 36 + i, QuickTransferRulesTest::vanillaMenuToInventory));
        }
        // the main inventory is the same number in both
        for (int i = 9; i < 36; i++) {
            assertEquals(i, QuickTransferRules.creativeInventoryIndex(true, i, QuickTransferRulesTest::vanillaMenuToInventory));
        }
    }

    @Test
    void onTheCreativeInventoryTabArmorCraftingAndOffhandAreNotTransferable() {
        for (int menu : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 45}) {
            assertEquals(-1, QuickTransferRules.creativeInventoryIndex(true, menu, QuickTransferRulesTest::vanillaMenuToInventory), "menu " + menu);
        }
        assertEquals(-1, QuickTransferRules.creativeInventoryIndex(true, 99, QuickTransferRulesTest::vanillaMenuToInventory));
        assertEquals(-1, QuickTransferRules.creativeInventoryIndex(true, -1, QuickTransferRulesTest::vanillaMenuToInventory));
    }

    @Test
    void onTheItemTabTheHotbarSlotsAreRealSlotsAndKeepTheirNumber() {
        for (int i = 0; i < 9; i++) {
            // the translator must not even be asked: it would give another answer
            assertEquals(i, QuickTransferRules.creativeInventoryIndex(false, i, menu -> {
                throw new AssertionError("asked on the item tab");
            }));
        }
        assertEquals(-1, QuickTransferRules.creativeInventoryIndex(false, 36, menu -> 0));
    }
}
