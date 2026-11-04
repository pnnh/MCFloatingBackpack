package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuickTransferRulesTest {
    @Test
    void anyContainerScreenIsInterceptableExceptCreative() {
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
    void creativeInventoryAndEmptyNamesAreNotInterceptable() {
        assertFalse(QuickTransferRules.isInterceptableScreenClass(
                "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen"));
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
}
