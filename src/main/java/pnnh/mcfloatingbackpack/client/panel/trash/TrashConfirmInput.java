package pnnh.mcfloatingbackpack.client.panel.trash;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;

/**
 * 022: what the player's mouse does while the confirmation dialog is up. Every press anywhere is taken: the "OK" button
 * sends what was waiting (confirmed), anything else, the cancel button, the mask, a place outside the panel, the trash
 * can panel, only closes the dialog. The renderer and this class read the same layout, built from the same wrapped text.
 */
@OnlyIn(Dist.CLIENT)
public final class TrashConfirmInput {
    private TrashConfirmInput() {
    }

    /** The question as it is shown, wrapped to the dialog's text width on the current panel. */
    public static List<FormattedCharSequence> lines(Minecraft minecraft) {
        FloatingPanelState panel = FloatingPanelState.get();
        Component text = Component.translatable("trash.mcfloatingbackpack.confirm.text", TrashConfirmState.get().destroyed());
        return minecraft.font.split(text, TrashConfirmLayout.textWidth(panel.getWidth()));
    }

    public static TrashConfirmLayout layout(Minecraft minecraft) {
        FloatingPanelState panel = FloatingPanelState.get();
        return TrashConfirmLayout.of(
                panel.getPosX(), panel.getPosY(), panel.getWidth(), panel.getHeight(), lines(minecraft).size(), minecraft.font.lineHeight);
    }

    /**
     * A press of any button anywhere.
     *
     * @return true if a dialog is up (the press is consumed whatever it hit)
     */
    public static boolean onPress(int button, double mouseX, double mouseY) {
        TrashConfirmState state = TrashConfirmState.get();
        if (!state.isPending()) {
            return false;
        }
        TrashConfirmLayout.Hit hit = layout(Minecraft.getInstance()).hit(mouseX, mouseY);
        // Whatever this press was, its release must not be taken for the end of a drag onto the trash can.
        TrashPanelInput.resetTracking();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit == TrashConfirmLayout.Hit.CONFIRM) {
            TrashActions.sendConfirmed();
        } else {
            state.cancel();
        }
        return true;
    }

    /** ESC while the dialog is up: it only withdraws the question. @return true if there was one */
    public static boolean onEscape() {
        TrashConfirmState state = TrashConfirmState.get();
        if (!state.isPending()) {
            return false;
        }
        state.cancel();
        return true;
    }
}
