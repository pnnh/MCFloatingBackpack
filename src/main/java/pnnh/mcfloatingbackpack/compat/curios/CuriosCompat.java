package pnnh.mcfloatingbackpack.compat.curios;

import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;

/**
 * 033: the one entry into the Curios support. The mod's constructor calls {@link #register()} only when Curios is loaded, and its
 * signature has no Curios type, so the main class never causes a Curios class to load; when Curios is not there, nothing here runs.
 */
public final class CuriosCompat {
    private CuriosCompat() {
    }

    /** Adds the Curios back slot to the places a backpack is worn (see {@code WornBackpacks}); both sides run it. */
    public static void register() {
        WornBackpacks.register(new CuriosWornSource());
    }
}
