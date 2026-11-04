package pnnh.mcfloatingbackpack.client.panel;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.network.BackpackClickPayload;

/**
 * Hit-testing, panel drag, and slot clicks for the floating backpack.
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingPanelInput {
    private static final ConsumedPressTracker PRESSES = new ConsumedPressTracker();

    private FloatingPanelInput() {
    }

    /** Call when a mouse event arrives while the panel is hidden: forget any presses it had swallowed. */
    public static void resetPressTracking() {
        PRESSES.clear();
    }

    /**
     * @return true if the event should be consumed (cancelled)
     */
    public static boolean onMouseButton(int button, int action, double mouseX, double mouseY) {
        if (!FloatingPanelState.get().isVisible()) {
            PRESSES.clear();
            return false;
        }
        boolean consumed = dispatchMouseButton(button, action, mouseX, mouseY);
        if (action == GLFW.GLFW_PRESS) {
            if (consumed) {
                PRESSES.press(button);
            }
            return consumed;
        }
        if (action == GLFW.GLFW_RELEASE) {
            // A release is swallowed only if its press was (or it ends a drag/scroll/resize, which
            // can only start from a swallowed press). Never because the cursor merely sits over the
            // panel: the panel can open between press and release, and swallowing that release
            // leaves the game thinking the button is still held.
            return PRESSES.release(button) || consumed;
        }
        return consumed;
    }

    private static boolean dispatchMouseButton(int button, int action, double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        boolean isRight = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (!state.isVisible() || (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && !isRight)) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            // Close button and resize handle sit on/inside the drag bar and body respectively, so
            // they must be checked before those broader hit tests claim the press first.
            if (!isRight && state.hitsCloseButton(mouseX, mouseY)) {
                FloatingPanelEvents.closePanel();
                return true;
            }
            if (!isRight && state.hitsResizeHandle(mouseX, mouseY)) {
                state.beginResize((int) mouseX, (int) mouseY);
                return true;
            }
            // Dragging the panel and the scrollbar stay left-button-only, but any press over the
            // panel must still be consumed so it can't leak through to whatever real screen sits
            // underneath the overlay.
            if (!isRight && state.hitsDragBar(mouseX, mouseY)) {
                state.beginDrag((int) mouseX, (int) mouseY);
                return true;
            }
            if (!isRight && PanelSlots.hitsScrollbar(state, mouseX, mouseY)) {
                if (PanelSlots.hitsScrollThumb(state, mouseX, mouseY)) {
                    state.beginScrollDrag((int) mouseY);
                } else {
                    state.setScrollRow(PanelSlots.scrollRowAt(state, mouseY));
                    state.beginScrollDrag((int) mouseY);
                }
                return true;
            }
            int slot = PanelSlots.indexAt(state, mouseX, mouseY);
            // The last reflowed row can run past the backpack's real slot count once visibleCols
            // no longer evenly divides its capacity; indexAt only knows the virtual grid, so the
            // real bound has to be checked here before trusting the index.
            if (slot >= 0 && slot < ClientBackpackView.get().slotCount()) {
                sendClick(slot, isRight);
                return true;
            }
            if (state.hitsBody(mouseX, mouseY) || state.contains(mouseX, mouseY)) {
                return true;
            }
            return false;
        }

        if (action == GLFW.GLFW_RELEASE && state.isDragging()) {
            finishDrag(state);
            return true;
        }

        if (action == GLFW.GLFW_RELEASE && state.isScrolling()) {
            state.endScrollDrag();
            return true;
        }

        if (action == GLFW.GLFW_RELEASE && state.isResizing()) {
            finishResize(state);
            return true;
        }

        return false;
    }

    public static boolean onMouseDragged(double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible()) {
            return false;
        }
        if (state.isDragging()) {
            state.updateDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (state.isScrolling()) {
            state.updateScrollDrag((int) mouseY);
            return true;
        }
        if (state.isResizing()) {
            state.updateResize((int) mouseX, (int) mouseY);
            return true;
        }
        return false;
    }

    private static void sendClick(int slotIndex, boolean rightClick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        BackpackRef ref = ClientBackpackView.get().getActiveRef();
        if (ref == null) {
            ref = BackpackRef.resolve(minecraft.player);
        }
        if (ref == null) {
            stateClose();
            return;
        }
        boolean shift = Screen.hasShiftDown()
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen);
        PacketDistributor.sendToServer(
                new BackpackClickPayload(
                        slotIndex,
                        shift,
                        rightClick,
                        screenOpen,
                        true,
                        ref.source().ordinal(),
                        ref.invIndex()));
    }

    private static void stateClose() {
        FloatingPanelState.get().setVisible(false);
        ClientBackpackView.get().clear();
    }

    private static void finishDrag(FloatingPanelState state) {
        Minecraft minecraft = Minecraft.getInstance();
        state.endDrag();
        state.clampToScreen(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
        FloatingPanelConfig.saveFrom(state);
    }

    private static void finishResize(FloatingPanelState state) {
        state.endResize();
        FloatingPanelConfig.saveFrom(state);
    }
}
