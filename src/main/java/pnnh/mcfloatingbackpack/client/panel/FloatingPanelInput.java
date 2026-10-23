package pnnh.mcfloatingbackpack.client.panel;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Hit-testing and drag handling for the floating panel. Consumes clicks on the
 * panel so they do not fall through to a container screen underneath.
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

    /**
     * @return true if the drag event should be consumed
     */
    public static boolean onMouseDragged(double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() || !state.isDragging()) {
            return false;
        }
        state.updateDrag((int) mouseX, (int) mouseY);
        return true;
    }

    public static void finishDragIfNeeded() {
        FloatingPanelState state = FloatingPanelState.get();
        if (state.isDragging()) {
            finishDrag(state);
        }
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
