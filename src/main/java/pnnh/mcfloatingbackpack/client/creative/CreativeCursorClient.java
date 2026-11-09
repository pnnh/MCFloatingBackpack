package pnnh.mcfloatingbackpack.client.creative;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.inventory.creative.CursorMirror;
import pnnh.mcfloatingbackpack.network.CreativeCursorReportPayload;

/**
 * 027: the client's side of the creative cursor. While the creative inventory screen is open and the panel is visible, the
 * cursor (which only the client has there) is reported to the server whenever it differs from what the server was last told
 * or told us; the server's answer to a panel operation arrives through {@link #onPush}. See {@code CreativeCursorBridge}.
 *
 * <p>{@link #flush} is called at the start of every mouse and key event on a screen (before the screen or the panel handles
 * it) and before the panel sends a request, so the server has the cursor of the moment before the click. Pressing E or ESC
 * to close the screen is such an event, so the last cursor reaches the server before the close packet.
 */
@OnlyIn(Dist.CLIENT)
public final class CreativeCursorClient {
    private static final CursorMirror<ItemStack> MIRROR =
            new CursorMirror<>(ItemStack::matches, ItemStack::copy, ItemStack.EMPTY);

    private CreativeCursorClient() {
    }

    /** Reports the cursor if the creative inventory is the current screen. */
    public static void flush() {
        flush(Minecraft.getInstance().screen);
    }

    /** Reports the cursor as it would be on {@code screen} (a screen that is about to open counts). */
    public static void flush(Screen screen) {
        if (!(screen instanceof CreativeModeInventoryScreen) || !FloatingPanelState.get().isVisible()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isCreative()) {
            return;
        }
        ItemStack carried = player.inventoryMenu.getCarried();
        if (MIRROR.shouldSend(carried)) {
            PacketDistributor.sendToServer(new CreativeCursorReportPayload(carried.copy()));
            MIRROR.sent(carried);
        }
    }

    /** The server changed the cursor (a panel operation). Ignored once the creative screen is gone. */
    public static void onPush(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        minecraft.player.inventoryMenu.setCarried(stack.copy());
        MIRROR.received(stack);
    }

    /** The creative screen is about to open or has closed: start again from an empty cursor. */
    public static void reset() {
        MIRROR.reset();
    }
}
