package pnnh.mcfloatingbackpack.client.render;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Registers the third-person backpack layer on the mod event bus
 * ({@link EntityRenderersEvent.AddLayers} is not a game-bus event).
 */
public final class BackpackRenderEvents {
    private BackpackRenderEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BackpackRenderEvents::onAddLayers);
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BackpackBackLayer(renderer));
            }
        }
    }
}
