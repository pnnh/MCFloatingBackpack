package pnnh.mcfloatingbackpack.compat.curios;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
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
        WornBackpacks.register(new CuriosWornSource("back", BackpackRef.Source.CURIOS, 10));
        // 061: the dedicated slot and the charm slot, after it; every one of them is registered whether or not the dedicated slot's start-up
        // switch is on (without the slot the source has no slots to look at)
        WornBackpacks.register(new CuriosWornSource("floating_backpack", BackpackRef.Source.CURIOS_DEDICATED, 11));
        WornBackpacks.register(new CuriosWornSource("charm", BackpackRef.Source.CURIOS_CHARM, 12));
    }

    /**
     * 061: tells Curios that a floating backpack goes into a slot type only if no other is there (see {@link BackpackCurioItem}). Both sides
     * run it, from the common setup, once the items exist; Curios reads what is registered here when an item's capability is first asked for.
     */
    public static void registerCurios() {
        for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[] {
            pnnh.mcfloatingbackpack.item.ModItems.LEATHER_FLOATING_BACKPACK.get(),
            pnnh.mcfloatingbackpack.item.ModItems.IRON_FLOATING_BACKPACK.get(),
            pnnh.mcfloatingbackpack.item.ModItems.GOLD_FLOATING_BACKPACK.get(),
            pnnh.mcfloatingbackpack.item.ModItems.DIAMOND_FLOATING_BACKPACK.get(),
            pnnh.mcfloatingbackpack.item.ModItems.NETHERITE_FLOATING_BACKPACK.get()
        }) {
            top.theillusivec4.curios.api.CuriosApi.registerCurio(item, new BackpackCurioItem());
        }
    }
}
