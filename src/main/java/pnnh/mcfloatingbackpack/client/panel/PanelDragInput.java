package pnnh.mcfloatingbackpack.client.panel;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Preview;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Release;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.PanelCursorRequest.Mode;
import pnnh.mcfloatingbackpack.network.BackpackCursorGesturePayload;

/**
 * The part of the panel's drag-distribute / drag-gather gesture (013) that talks to the game: reading the
 * cursor stack and the slot under the mouse, sending requests, and being driven once per frame. The rules
 * live in {@link PanelDragGesture}.
 *
 * <p>Ctrl and Shift are read only to step aside: Ctrl belongs to the 011/012 quick transfer, Shift keeps its
 * old meaning, so the gesture never starts while either is held and is dropped if one goes down mid-drag.
 */
@OnlyIn(Dist.CLIENT)
public final class PanelDragInput {
    private static PanelDragGesture<ItemStack> gesture = newGesture();
    private static int activeButton = -1;
    /** The screen (the host screen or a container screen) the gesture started on; it ends when that changes. */
    private static Screen activeScreen;

    private PanelDragInput() {
    }

    private static PanelDragGesture<ItemStack> newGesture() {
        return new PanelDragGesture<>(ItemStackTransferOps.INSTANCE, Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    public static boolean isActive() {
        return gesture.isActive();
    }

    /**
     * A left/right press on a panel slot.
     *
     * @return true if the press is held back until release (an item is on the cursor); false if the caller
     *     should send the ordinary click right away
     */
    public static boolean beginFromPanel(int slot, boolean right, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Screen.hasControlDown() || Screen.hasShiftDown()) {
            gesture.cancel();
            return false;
        }
        // 014: with no container screen open Alt+click swaps with the main-hand item, which must stay an
        // immediate click, so this gesture (which holds the click back until release) steps aside.
        if (Screen.hasAltDown() && !ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)) {
            gesture.cancel();
            return false;
        }
        activeButton = right ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_LEFT;
        activeScreen = minecraft.screen;
        ClientBackpackView view = ClientBackpackView.get();
        boolean taken = gesture.beginPress(
                slot, right, FloatingPanelRenderer.cursorStack(minecraft), view::getStackInSlot, mouseX, mouseY, Util.getMillis());
        if (!gesture.isActive()) {
            activeButton = -1;
        }
        return taken;
    }

    /** A release of {@code button}. @return true if it ended a gesture that was waiting for it. */
    public static boolean onRelease(int button, double mouseX, double mouseY) {
        if (!gesture.isActive() || button != activeButton) {
            return false;
        }
        settle(mouseX, mouseY);
        return true;
    }

    /** Per-frame driver; call with the current GUI-scaled mouse position. */
    public static void tick(Minecraft minecraft, double mouseX, double mouseY) {
        if (!gesture.isActive()) {
            return;
        }
        if (invalid(minecraft) || Screen.hasControlDown() || Screen.hasShiftDown()) {
            stop();
            return;
        }
        boolean down = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), activeButton) == GLFW.GLFW_PRESS;
        if (!down) {
            // The release event never reached us (it was taken elsewhere); finish the gesture here.
            settle(mouseX, mouseY);
            return;
        }
        List<Integer> gathered = advance(minecraft, mouseX, mouseY);
        if (!gathered.isEmpty()) {
            send(Mode.GATHER_DRAG, toArray(gathered));
        }
    }

    /** Ends a gesture whose context is gone (panel hidden, screen changed, cursor emptied); called every client tick. */
    public static void stopIfInvalid(Minecraft minecraft) {
        if (gesture.isActive() && invalid(minecraft)) {
            stop();
        }
    }

    public static void reset() {
        gesture = newGesture();
        activeButton = -1;
        activeScreen = null;
    }

    /** What a release now would do to the slots, for drawing; null when nothing is previewed. */
    public static Preview<ItemStack> preview() {
        if (!gesture.isActive()) {
            return null;
        }
        return gesture.preview(FloatingPanelRenderer.cursorStack(Minecraft.getInstance()), ClientBackpackView.get()::getStackInSlot);
    }

    private static void settle(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        // The gathering state sends its last slots here, so the slot under the release point counts.
        List<Integer> gathered = advance(minecraft, mouseX, mouseY);
        if (!gathered.isEmpty()) {
            send(Mode.GATHER_DRAG, toArray(gathered));
        }
        Release release = gesture.release(slotAt(mouseX, mouseY));
        activeButton = -1;
        switch (release.kind()) {
            case CLICK -> FloatingPanelInput.sendClick(release.slots().get(0), release.right());
            case DISTRIBUTE -> send(release.right() ? Mode.DISTRIBUTE_RIGHT : Mode.DISTRIBUTE_LEFT, toArray(release.slots()));
            case GATHER_ALL -> send(Mode.GATHER_ALL, toArray(release.slots()));
            case NONE -> {
            }
        }
    }

    private static List<Integer> advance(Minecraft minecraft, double mouseX, double mouseY) {
        return gesture.advance(
                PanelDragInput::slotAt,
                ClientBackpackView.get()::getStackInSlot,
                FloatingPanelRenderer.cursorStack(minecraft),
                mouseX,
                mouseY);
    }

    private static void stop() {
        gesture.cancel();
        activeButton = -1;
    }

    private static boolean invalid(Minecraft minecraft) {
        if (minecraft.player == null
                || !FloatingPanelState.get().isVisible()
                || minecraft.screen != activeScreen
                // 020: the screen stopped hosting the panel (its mod was switched off in the config).
                || !FloatingPanelEvents.hostsPanel(minecraft.screen)) {
            return true;
        }
        // Distributing needs something to distribute; gathering starts with an empty cursor while the pickup
        // is still on its way back from the server, so only the distributing state checks it.
        return gesture.state() == PanelDragGesture.State.DISTRIBUTING
                && FloatingPanelRenderer.cursorStack(minecraft).isEmpty();
    }

    private static int slotAt(double x, double y) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible()) {
            return -1;
        }
        int index = PanelSlots.indexAt(state, x, y);
        return index >= 0 && index < ClientBackpackView.get().slotCount() ? index : -1;
    }

    private static void send(Mode mode, int[] slots) {
        boolean screenOpen = ContainerScreenCompat.isOpenContainerScreen(Minecraft.getInstance().screen);
        PacketDistributor.sendToServer(BackpackCursorGesturePayload.of(mode, slots, screenOpen));
    }

    private static int[] toArray(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
}
