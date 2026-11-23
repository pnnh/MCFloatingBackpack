package pnnh.mcfloatingbackpack.client.panel;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import pnnh.mcfloatingbackpack.client.compat.NetworkHeld;
import pnnh.mcfloatingbackpack.client.compat.NetworkTerminalClients;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmState;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.network.BackpackNetworkTakePayload;

/**
 * 060 (fix after the first real-machine test): the item picked up with a plain click on a network terminal's list, when the terminal keeps it
 * as its own "active entry" and not as the menu's carried stack (Integrated Terminals does), is dropped into the floating backpack by a left
 * click on the panel. Without this the panel could neither see it nor take it: its cursor logic only follows the menu's carried stack, and the
 * terminal draws its item underneath the panel. The click asks the server for the held amount (the same request as the Ctrl+click, with an
 * amount) and lets go of the terminal's active entry; the server does the rest and nothing is taken out of the network on the client's say-so
 * beyond one stack.
 */
public final class NetworkHeldDrop {
    private NetworkHeldDrop() {
    }

    /** What is held on the terminal's own cursor on this screen, if the panel is up and the menu's carried stack is empty. */
    public static Optional<NetworkHeld> heldOn(Minecraft minecraft, Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)
                || minecraft.player == null
                || !ContainerScreenCompat.isOpenContainerScreen(screen)
                || !FloatingPanelState.get().isVisible()
                || !minecraft.player.containerMenu.getCarried().isEmpty()) {
            return Optional.empty();
        }
        return NetworkTerminalClients.held(container);
    }

    /**
     * A press of the left button on the panel's body while something is held on the terminal's cursor.
     *
     * @return true if the press was taken (it must not reach the panel's own handling or the screen)
     */
    public static boolean tryDrop(Screen screen, int button, double mouseX, double mouseY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible()
                || !state.hitsBody(mouseX, mouseY)
                || state.hitsCloseButton(mouseX, mouseY)
                || state.hitsResizeHandle(mouseX, mouseY)
                || TrashConfirmState.get().isPending()) {
            return false;
        }
        Optional<NetworkHeld> held = heldOn(Minecraft.getInstance(), screen);
        if (held.isEmpty()) {
            return false;
        }
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        PacketDistributor.sendToServer(new BackpackNetworkTakePayload(
                container.getMenu().containerId, held.get().what(), 0L, held.get().amount()));
        NetworkTerminalClients.release(container);
        return true;
    }
}
