package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickTransferRulesTest {
    @Test
    void vanillaContainerScreensAreSupported() {
        assertTrue(QuickTransferRules.isSupportedScreenClass(
                "net.minecraft.client.gui.screens.inventory.InventoryScreen"));
        assertTrue(QuickTransferRules.isSupportedScreenClass(
                "net.minecraft.client.gui.screens.inventory.ContainerScreen"));
        assertTrue(QuickTransferRules.isSupportedScreenClass(
                "net.minecraft.client.gui.screens.inventory.CraftingScreen"));
    }

    @Test
    void creativeInventoryIsExcluded() {
        assertFalse(QuickTransferRules.isSupportedScreenClass(
                "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen"));
    }

    @Test
    void thirdPartyScreensAreExcluded() {
        assertFalse(QuickTransferRules.isSupportedScreenClass(
                "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase"));
        assertFalse(QuickTransferRules.isSupportedScreenClass(
                "net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen"));
        assertFalse(QuickTransferRules.isSupportedScreenClass(
                "com.tiviacz.travelersbackpack.client.screens.BackpackScreen"));
        // A mod subclass of a vanilla screen lives in the mod's package, so it is excluded too.
        assertFalse(QuickTransferRules.isSupportedScreenClass("com.example.MyChestScreen"));
        assertFalse(QuickTransferRules.isSupportedScreenClass(null));
        assertFalse(QuickTransferRules.isSupportedScreenClass(""));
    }

    @Test
    void onlyMainInventoryAndHotbarSlotsAreTransferable() {
        for (int i = 0; i < 36; i++) {
            assertTrue(QuickTransferRules.isTransferableInventorySlot(true, i, true), "slot " + i);
        }
        // armor 36-39 and offhand 40 share the player Inventory but are not transferable
        for (int i = 36; i <= 40; i++) {
            assertFalse(QuickTransferRules.isTransferableInventorySlot(true, i, true), "slot " + i);
        }
        // chest cells, crafting grid, result slot: container is not the player inventory
        assertFalse(QuickTransferRules.isTransferableInventorySlot(false, 3, true));
        // a disabled slot never counts
        assertFalse(QuickTransferRules.isTransferableInventorySlot(true, 3, false));
        assertFalse(QuickTransferRules.isTransferableInventorySlot(true, -1, true));
    }
}
