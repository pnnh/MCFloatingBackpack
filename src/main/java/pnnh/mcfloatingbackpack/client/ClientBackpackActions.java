package pnnh.mcfloatingbackpack.client;

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
