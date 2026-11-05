package pnnh.mcfloatingbackpack.client.panel.trash;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.PanelDragInput;
import pnnh.mcfloatingbackpack.client.panel.QuickTransferInput;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.trash.TrashRequest;
import pnnh.mcfloatingbackpack.network.BackpackTrashPayload;

/**
 * 021: what a press or a release on the trash can panel does. The panel lies outside the floating panel's rectangle,
 * so its presses are claimed here before the floating panel's own hit tests; a press on it never reaches the screen
 * underneath or any of the main area's gestures. Every move of items is a request to the server.
 */
@OnlyIn(Dist.CLIENT)
public final class TrashPanelInput {
    private static final TrashDropTracker DROPS = new TrashDropTracker();

    private TrashPanelInput() {
    }

    /** The panel's geometry for the floating panel's current position and width. */
    public static TrashPanelLayout layout() {
        FloatingPanelState state = FloatingPanelState.get();
        return TrashPanelLayout.of(state.getPosX(), state.getPosY(), state.getWidth());
    }

    /** Whether the point is on the expanded trash can panel. */
    public static boolean covers(double mouseX, double mouseY) {
        return TrashPanelState.get().isOpen() && layout().contains(mouseX, mouseY);
    }

    /** Forgets where presses went down (the panel was hidden, or the world was left). */
    public static void resetTracking() {
        DROPS.clear();
    }

    /** Records where a button went down, for {@link #onRelease}. Called for every press, consumed or not. */
    public static void notePress(int button, double mouseX, double mouseY) {
        DROPS.press(button, covers(mouseX, mouseY));
    }

    /**
     * A left or right press anywhere.
     *
     * @return true if it was on the expanded panel and is therefore consumed
     */
    public static boolean onPress(int button, double mouseX, double mouseY) {
        if (!covers(mouseX, mouseY)) {
            return false;
        }
        // Only the left button does anything; Shift and a drag of the main area under way are swallowed too.
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || Screen.hasShiftDown() || PanelDragInput.isActive()) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!FloatingPanelRenderer.cursorStack(minecraft).isEmpty()) {
            // Anywhere on the panel, the title bar and the gaps included, with or without Ctrl: the cursor stack goes in.
            send(TrashRequest.Action.ADD, new int[0], minecraft);
            return true;
        }
        // Empty cursor: a group is taken onto the cursor, or with Ctrl moved (dragging on moves more) into the main area.
        int cell = layout().cellAt(mouseX, mouseY);
        if (Screen.hasControlDown()) {
            QuickTransferInput.beginFromTrash(cell, mouseX, mouseY);
        } else if (cell >= 0 && !ClientBackpackView.get().trash().get(cell).isEmpty()) {
            send(TrashRequest.Action.TAKE, new int[] {cell}, minecraft);
        }
        return true;
    }

    /**
     * A button came up. If it is the left button, went down outside this panel and comes up on it, the player dragged
     * something here: the cursor stack goes into the trash can and no other gesture settles this release.
     *
     * @return true if it was such a drop
     */
    public static boolean onRelease(int button, double mouseX, double mouseY) {
        if (!DROPS.release(button, covers(mouseX, mouseY))) {
            return false;
        }
        // Shift and Ctrl belong to the selection and the transfers, which never carry anything on the cursor.
        if (Screen.hasShiftDown() || Screen.hasControlDown()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PanelDragInput.cancel();
        // Sent even if the cursor looks empty: a pickup that is still on its way back from the server has not emptied
        // it yet as far as this client knows. The server goes by the real cursor and ignores an empty one.
        send(TrashRequest.Action.ADD, new int[0], minecraft);
        clearCursorLocally(minecraft);
        return true;
    }

    /**
     * What the server is about to do, shown at once, so the game's own screen finishes its drag with nothing held (it
     * would otherwise throw the stack out of the window or put it in a slot under this panel). The server's reply
     * corrects it if the request was refused.
     */
    private static void clearCursorLocally(Minecraft minecraft) {
        if (minecraft.player != null
                && minecraft.player.containerMenu != null
                && ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)) {
            minecraft.player.containerMenu.setCarried(ItemStack.EMPTY);
        } else {
            ClientBackpackView.get().applySlotUpdate(PanelArea.MAIN, -1, ItemStack.EMPTY, ItemStack.EMPTY);
        }
    }

    static void send(TrashRequest.Action action, int[] slots, Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        boolean screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen);
        PacketDistributor.sendToServer(BackpackTrashPayload.of(action, slots, screenOpen));
    }
}
