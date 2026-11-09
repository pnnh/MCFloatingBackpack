package pnnh.mcfloatingbackpack.compat.accessories;

import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;

import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * 034: the client's part of the Accessories support: a renderer for each tier that draws nothing (see {@link BackpackAccessoryRenderer}).
 * Only the client's setup calls this, and only when Accessories is loaded, so a dedicated server never loads this class.
 */
@OnlyIn(Dist.CLIENT)
public final class AccessoriesClientCompat {
    private AccessoriesClientCompat() {
    }

    /**
     * Registers the renderers. Accessories keeps what is registered until its resources are (re)loaded, so this must run before the first
     * reload: the client setup event does. One new renderer per tier, because Accessories wraps a renderer that two items share.
     */
    public static void registerRenderers() {
        register(ModItems.LEATHER_FLOATING_BACKPACK.get());
        register(ModItems.IRON_FLOATING_BACKPACK.get());
        register(ModItems.GOLD_FLOATING_BACKPACK.get());
        register(ModItems.DIAMOND_FLOATING_BACKPACK.get());
        register(ModItems.NETHERITE_FLOATING_BACKPACK.get());
    }

    private static void register(Item item) {
        AccessoriesRendererRegistry.registerRenderer(item, () -> new BackpackAccessoryRenderer());
    }
}
