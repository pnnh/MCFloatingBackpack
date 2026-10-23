package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat.CompatTarget;

class ContainerScreenCompatTest {
    @Test
    void tagsSophisticatedCoreStorageScreen() {
        assertEquals(
                CompatTarget.SOPHISTICATED,
                ContainerScreenCompat.identifyByClassName(
                        "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase"));
    }

    @Test
    void tagsSophisticatedBackpackScreen() {
        assertEquals(
                CompatTarget.SOPHISTICATED,
                ContainerScreenCompat.identifyByClassName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen"));
    }

    @Test
    void tagsTravelersBackpackScreen() {
        assertEquals(
                CompatTarget.TRAVELERS,
                ContainerScreenCompat.identifyByClassName(
                        "com.tiviacz.travelersbackpack.client.screens.BackpackScreen"));
        assertEquals(
                CompatTarget.TRAVELERS,
                ContainerScreenCompat.identifyByClassName(
                        "com.tiviacz.travelersbackpack.client.screens.AbstractBackpackScreen"));
    }

    @Test
    void vanillaAndNullAreNotTaggedAsBackpackMods() {
        assertEquals(CompatTarget.NONE, ContainerScreenCompat.identifyByClassName(null));
        assertEquals(CompatTarget.NONE, ContainerScreenCompat.identifyByClassName(""));
        assertEquals(
                CompatTarget.UNKNOWN_CONTAINER,
                ContainerScreenCompat.identifyByClassName(
                        "net.minecraft.client.gui.screens.inventory.InventoryScreen"));
        assertNotEquals(
                CompatTarget.SOPHISTICATED,
                ContainerScreenCompat.identifyByClassName(
                        "net.minecraft.client.gui.screens.inventory.InventoryScreen"));
    }
}
