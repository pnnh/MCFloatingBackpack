package pnnh.mcfloatingbackpack.client.panel;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
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

        if (action == GLFW.GLFW_RELEASE && state.contains(mouseX, mouseY)) {
            return true;
        }

        return false;
    }

    public static boolean onMouseDragged(double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() || !state.isDragging()) {
            return false;
        }
        state.updateDrag((int) mouseX, (int) mouseY);
        return true;
    }

    private static void sendClick(int slotIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        boolean shift = Screen.hasShiftDown()
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen);
        PacketDistributor.sendToServer(
                new BackpackClickPayload(slotIndex, shift, screenOpen, true));
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
