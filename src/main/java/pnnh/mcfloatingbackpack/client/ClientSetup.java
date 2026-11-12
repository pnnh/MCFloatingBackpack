package pnnh.mcfloatingbackpack.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.accessories.AccessoriesClientCompat;
import pnnh.mcfloatingbackpack.compat.ConfigFileWatch;
import pnnh.mcfloatingbackpack.client.render.BackpackRenderEvents;
import pnnh.mcfloatingbackpack.search.SearchTerms;

/**
 * Client-only wiring for the floating backpack overlay and hotkeys.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        MCFloatingBackpack.LOGGER.info("MCFloatingBackpack client setup");
        modEventBus.addListener(FloatingBackpackKeys::registerKeys);
        // 034: Accessories draws a default look for an item it has no renderer for; ours draws nothing (the backpack is drawn by our own
        // layer). Accessories keeps the registered renderers until its first resource reload, so they are registered here, in the setup.
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            if (ModList.get().isLoaded("accessories")) {
                AccessoriesClientCompat.registerRenderers();
            }
        }));
        BackpackRenderEvents.register(modEventBus);
        // 043: what search terms providers gave is cached per item; a language switch, F3+T or a resource pack reloads the resources,
        // and the providers' terms may be different after it.
        modEventBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> SearchTerms.onResourcesReloaded()));
        FloatingPanelEvents.init();
        // 020: tell the player in chat when NeoForge had to set a broken config file aside.
        modEventBus.addListener(ConfigFileWatch::onLoading);
        modEventBus.addListener(ConfigFileWatch::onReloading);
        // 020: the config screen, reachable from the mod list.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
