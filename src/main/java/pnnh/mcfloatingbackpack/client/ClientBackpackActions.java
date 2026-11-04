package pnnh.mcfloatingbackpack.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.network.BackpackOpenRequestPayload;

/**
 * Client-only helpers for item use / overlay open requests.
 */
public final class ClientBackpackActions {
    private ClientBackpackActions() {
    }

    /** Toggle floating panel for the main-hand (or resolved) backpack. */
    public static void toggleFromUse() {
        // Vanilla repeats "use item" every 4 ticks while the use key is held. A backpack use toggles
        // the panel, so a held (or stuck) key would flicker it open/closed; drop the held state so
        // one press is one toggle.
        Minecraft.getInstance().options.keyUse.setDown(false);
        FloatingPanelState state = FloatingPanelState.get();
        if (state.isVisible()) {
            state.setVisible(false);
            PacketDistributor.sendToServer(BackpackOpenRequestPayload.close());
            FloatingPanelEvents.syncMouseToPanelVisibility();
            return;
        }
        PacketDistributor.sendToServer(BackpackOpenRequestPayload.toggleMainHand());
    }

    public static void openResolved(BackpackRef ref) {
        if (ref == null) {
            return;
        }
        PacketDistributor.sendToServer(BackpackOpenRequestPayload.open(ref));
    }
}
