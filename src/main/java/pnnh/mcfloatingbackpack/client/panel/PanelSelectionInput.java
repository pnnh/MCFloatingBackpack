package pnnh.mcfloatingbackpack.client.panel;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest.Direction;
import pnnh.mcfloatingbackpack.network.BackpackQuickTransferPayload;

/**
 * The part of the panel selection (014) that talks to the game: modifier keys, the slot under the mouse and
 * being driven once per frame. The rules live in {@link SelectionGesture} and {@link PanelSelection}.
 *
 * <p>Ctrl pressed on a <em>selected</em> slot starts a batch that moves every selected stack out of the backpack
 * when the button comes up (the request is the 011/012 one). Ctrl on an unselected slot is left to 011/012.
 *
 * <p>Shift belongs to the selection inside the panel: a Shift press that is not a selection gesture (right
 * button, or together with Ctrl) is swallowed so it cannot fall through to an ordinary click.
 */
@OnlyIn(Dist.CLIENT)
public final class PanelSelectionInput {
    private static final SelectionGesture GESTURE = new SelectionGesture();
    private static int activeButton = -1;
    /** The screen (the host screen or a container screen) the gesture started on; it ends when that changes. */
    private static Screen activeScreen;

    private PanelSelectionInput() {
    }

    /**
     * A press on a real panel slot.
     *
     * @return true if the press was taken (selection gesture, or swallowed); false if the caller should carry
     *     on with the other panel gestures
     */
    public static boolean beginFromPanel(int slot, boolean right, double mouseX, double mouseY) {
        // A second button pressed while a gesture is under way does nothing.
        if (GESTURE.isActive()) {
            return true;
        }
        boolean shift = Screen.hasShiftDown();
        boolean ctrl = Screen.hasControlDown();
        if (shift) {
            if (!right && !ctrl) {
                activeButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
                activeScreen = Minecraft.getInstance().screen;
                GESTURE.beginSelect(slot, mouseX, mouseY);
            }
            return true;
        }
        if (ctrl) {
            // Only a left press on an already selected slot with an empty cursor; anything else is 011/012's.
            if (!right && GESTURE.beginBatch(slot, PanelSelection.get(), cursorIsEmpty(Minecraft.getInstance()))) {
                activeButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
                activeScreen = Minecraft.getInstance().screen;
                return true;
            }
        } else {
            // An ordinary click on a slot (Alt included) ends the selection, then carries on as usual.
            PanelSelection.get().clear();
        }
        return false;
    }

    /** A release of {@code button}. @return true if it ended a gesture that was waiting for it. */
    public static boolean onRelease(int button, double mouseX, double mouseY) {
        if (!GESTURE.isActive() || button != activeButton) {
            return false;
        }
        settle(mouseX, mouseY);
        return true;
    }

    /** Per-frame driver; call with the current GUI-scaled mouse position. */
    public static void tick(Minecraft minecraft, double mouseX, double mouseY) {
        if (!GESTURE.isActive()) {
            return;
        }
        boolean batch = GESTURE.state() == SelectionGesture.State.BATCH_PENDING;
        // Pressing Ctrl ends a select drag; a batch needs Ctrl, but that is only checked when it is released.
        if (invalid(minecraft) || (!batch && Screen.hasControlDown())) {
            stop();
            return;
        }
        boolean down = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), activeButton) == GLFW.GLFW_PRESS;
        if (!down) {
            // The release event never reached us (it was taken elsewhere); finish the gesture here.
            settle(mouseX, mouseY);
            return;
        }
        if (!batch) {
            GESTURE.advance(PanelSelectionInput::slotAt, PanelSelection.get(), mouseX, mouseY);
        }
    }

    /** Ends a gesture whose context is gone (panel hidden, screen changed); called every client tick. */
    public static void stopIfInvalid(Minecraft minecraft) {
        if (GESTURE.isActive() && invalid(minecraft)) {
            stop();
        }
    }

    public static void reset() {
        stop();
        PanelSelection.get().clear();
        activeScreen = null;
    }

    private static void settle(double mouseX, double mouseY) {
        PanelSelection selection = PanelSelection.get();
        if (GESTURE.state() == SelectionGesture.State.BATCH_PENDING) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientBackpackView view = ClientBackpackView.get();
            int[] slots = GESTURE.releaseBatch(
                    Screen.hasControlDown(),
                    Screen.hasShiftDown(),
                    FloatingPanelState.get().isVisible(),
                    minecraft.screen == activeScreen,
                    cursorIsEmpty(minecraft),
                    selection,
                    i -> i < view.slotCount() && !view.getStackInSlot(i).isEmpty());
            if (slots.length > 0) {
                // "TO_INVENTORY" means out of the backpack; the server picks the open container or the inventory.
                PacketDistributor.sendToServer(BackpackQuickTransferPayload.of(Direction.TO_INVENTORY, slots));
            }
        } else {
            // One last step so the slot under the release point counts.
            GESTURE.advance(PanelSelectionInput::slotAt, selection, mouseX, mouseY);
            GESTURE.release(selection);
        }
        activeButton = -1;
    }

    private static boolean cursorIsEmpty(Minecraft minecraft) {
        return FloatingPanelRenderer.cursorStack(minecraft).isEmpty();
    }

    private static void stop() {
        GESTURE.cancel();
        activeButton = -1;
    }

    private static boolean invalid(Minecraft minecraft) {
        return minecraft.player == null
                || !FloatingPanelState.get().isVisible()
                || minecraft.screen != activeScreen
                // 020: the screen stopped hosting the panel (its mod was switched off in the config).
                || !FloatingPanelEvents.hostsPanel(minecraft.screen);
    }

    private static int slotAt(double x, double y) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible()) {
            return -1;
        }
        int index = PanelSlots.indexAt(state, x, y);
        return index >= 0 && index < ClientBackpackView.get().slotCount() ? index : -1;
    }
}
