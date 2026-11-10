package pnnh.mcfloatingbackpack.compat.accessories;

import io.wispforest.accessories.api.AccessoriesAPI;

import net.minecraft.world.item.Item;

import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;
import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * 034: the one entry into the Accessories support that both sides run. The mod's constructor calls {@link #register()} only when
 * Accessories is loaded, and its signature has no Accessories type, so the main class never causes an Accessories class to load. The
 * client also registers its renderers ({@link AccessoriesClientCompat}).
 */
public final class AccessoriesCompat {
    private AccessoriesCompat() {
    }

    /** Adds the Accessories back slot to the places a backpack is worn (see {@code WornBackpacks}); both sides run it. */
    public static void register() {
        WornBackpacks.register(new AccessoriesWornSource());
    }

    /**
     * 036: tells Accessories that a backpack is not equipped by a right click (see {@link BackpackAccessory}). Both sides run it,
     * from the common setup, once the items exist.
     */
    public static void registerAccessories() {
        for (Item item : new Item[] {
            ModItems.LEATHER_FLOATING_BACKPACK.get(),
            ModItems.IRON_FLOATING_BACKPACK.get(),
            ModItems.GOLD_FLOATING_BACKPACK.get(),
            ModItems.DIAMOND_FLOATING_BACKPACK.get(),
            ModItems.NETHERITE_FLOATING_BACKPACK.get()
        }) {
            AccessoriesAPI.registerAccessory(item, new BackpackAccessory());
        }
    }
}
