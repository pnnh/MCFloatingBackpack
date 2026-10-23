package pnnh.mcfloatingbackpack.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * Client-only wiring for the floating backpack overlay and hotkeys.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void register(IEventBus modEventBus) {
        MCFloatingBackpack.LOGGER.info("MCFloatingBackpack client setup");
        modEventBus.addListener(FloatingBackpackKeys::registerKeys);
        FloatingPanelEvents.init();
    }
}
