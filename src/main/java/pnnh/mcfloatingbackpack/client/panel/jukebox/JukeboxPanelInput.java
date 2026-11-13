package pnnh.mcfloatingbackpack.client.panel.jukebox;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.jukebox.JukeboxPlayback;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.PanelDragInput;
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelLayout.Button;
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelLayout.Toggle;

/**
 * 053: the presses on the player page of the mobile jukebox. The page lies outside the floating panel's rectangle, so its presses are claimed
 * here before the floating panel's own hit tests; a press on it never reaches the screen underneath or any of the main area's gestures. Only
 * the left button does anything, and a press on a button or a switch does what it says; everything else on the page is taken and ignored.
 */
@OnlyIn(Dist.CLIENT)
public final class JukeboxPanelInput {
    private JukeboxPanelInput() {
    }

    /** The page's geometry for the floating panel's current position and width. */
    public static JukeboxPanelLayout layout() {
        FloatingPanelState state = FloatingPanelState.get();
        return JukeboxPanelLayout.of(state.getPosX(), state.getPosY(), state.getWidth());
    }

    /** Whether the point is on the expanded page. */
    public static boolean covers(double mouseX, double mouseY) {
        return JukeboxPanelState.get().isOpen() && layout().contains(mouseX, mouseY);
    }

    /**
     * A left or right press anywhere.
     *
     * @return true if it was on the expanded page and is therefore consumed
     */
    public static boolean onPress(int button, double mouseX, double mouseY) {
        if (!covers(mouseX, mouseY)) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || Screen.hasShiftDown() || PanelDragInput.isActive()) {
            return true;
        }
        JukeboxPanelLayout layout = layout();
        Button pressed = layout.buttonAt(mouseX, mouseY);
        Toggle toggled = layout.toggleAt(mouseX, mouseY);
        if (pressed == null && toggled == null) {
            return true;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (pressed != null) {
            switch (pressed) {
                case PREVIOUS -> JukeboxPlayback.previous();
                case PLAY_STOP -> {
                    if (JukeboxPlayback.status().playing()) {
                        JukeboxPlayback.stop();
                    } else {
                        JukeboxPlayback.play();
                    }
                }
                case REWIND -> JukeboxPlayback.rewind();
                case NEXT -> JukeboxPlayback.next();
            }
        } else if (toggled == Toggle.LOOP_ONE) {
            JukeboxPlayback.toggleLoopOne();
        } else {
            JukeboxPlayback.toggleShuffle();
        }
        return true;
    }
}
