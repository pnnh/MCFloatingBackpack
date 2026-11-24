package pnnh.mcfloatingbackpack.client.panel.combined;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.network.CombinedSelectPayload;

/**
 * 065: what a press or the wheel on the combined bars does. The bars lie outside the floating panel's rectangle (above it), so their presses
 * are claimed here before the panel's own hit tests, like the trash can page's: a press on them never reaches the screen underneath or any of
 * the panel's gestures. Choosing a tab is a request to the server; nothing changes on the client until the server answers.
 */
@OnlyIn(Dist.CLIENT)
public final class CombinedBarInput {
    /** How far an arrow click or one notch of the wheel moves the tabs. */
    private static final int STEP = 30;

    private CombinedBarInput() {
    }

    /** Whether the point is on the bars (and they are shown). */
    public static boolean covers(double mouseX, double mouseY) {
        return CombinedBars.shown() && CombinedBars.layout().contains(mouseX, mouseY);
    }

    /**
     * A left or right press anywhere.
     *
     * @return true if it was on the bars and is therefore consumed
     */
    public static boolean onPress(int button, double mouseX, double mouseY) {
        if (!CombinedBars.shown()) {
            return false;
        }
        CombinedBarLayout layout = CombinedBars.layout();
        if (!layout.contains(mouseX, mouseY)) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        ClientCombinedState state = ClientCombinedState.get();
        int arrow = layout.hitArrow(mouseX, mouseY);
        if (arrow != 0) {
            state.setTabScroll(layout.clampScroll(state.tabScroll() + arrow * STEP));
            return true;
        }
        int tab = layout.hitTab(mouseX, mouseY, state.tabScroll());
        if (tab >= 0) {
            select(tab);
        }
        return true;
    }

    /** The wheel over the bars moves the tabs; it is taken whether or not there is anything to move. */
    public static boolean onScroll(double scrollDeltaY, double mouseX, double mouseY) {
        if (scrollDeltaY == 0 || !covers(mouseX, mouseY)) {
            return false;
        }
        CombinedBarLayout layout = CombinedBars.layout();
        ClientCombinedState state = ClientCombinedState.get();
        state.setTabScroll(layout.clampScroll(state.tabScroll() + (scrollDeltaY > 0 ? -STEP : STEP)));
        return true;
    }

    private static void select(int index) {
        ClientCombinedState state = ClientCombinedState.get();
        if (index == state.selected() || state.loading()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // what is held (on the panel's cursor, or on a container screen's) would stay behind in the backpack it was taken from
        if (!FloatingPanelRenderer.cursorStack(minecraft).isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("message.mcfloatingbackpack.combined.empty_cursor"), true);
            }
            return;
        }
        state.beginLoading();
        PacketDistributor.sendToServer(CombinedSelectPayload.select(index, state.identityAt(index).orElse(null)));
    }
}
