package pnnh.mcfloatingbackpack.compat.accessories;

import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;

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
}
