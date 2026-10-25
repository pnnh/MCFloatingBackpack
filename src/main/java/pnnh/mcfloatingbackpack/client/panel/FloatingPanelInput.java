package pnnh.mcfloatingbackpack.client.panel;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.network.BackpackClickPayload;

/**
 * Hit-testing, panel drag, and slot clicks for the floating backpack.
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingPanelInput {
    private FloatingPanelInput() {
    }

    /**
     * @return true if the event should be consumed (cancelled)
     */
    public static boolean onMouseButton(int button, int action, double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            if (state.hitsDragBar(mouseX, mouseY)) {
                state.beginDrag((int) mouseX, (int) mouseY);
                return true;
            }
            if (PanelSlots.hitsScrollbar(state, mouseX, mouseY)) {
                if (PanelSlots.hitsScrollThumb(state, mouseX, mouseY)) {
                    state.beginScrollDrag((int) mouseY);
                } else {
                    state.setScrollRow(PanelSlots.scrollRowAt(state, mouseY));
                    state.beginScrollDrag((int) mouseY);
                }
                return true;
            }
            int slot = PanelSlots.indexAt(state, mouseX, mouseY);
            if (slot >= 0) {
                sendClick(slot);
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

        if (action == GLFW.GLFW_RELEASE && state.contains(mouseX, mouseY)) {
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
        return false;
    }

    private static void sendClick(int slotIndex) {
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
}
