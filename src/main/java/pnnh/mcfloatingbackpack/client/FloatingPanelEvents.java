package pnnh.mcfloatingbackpack.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelConfig;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelInput;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.network.MergePanelCursorPayload;

/**
 * Client overlay: render, toggle, and mouse routing for the floating panel.
 * Never opens a Screen for the panel itself.
 */
@EventBusSubscriber(modid = MCFloatingBackpack.MOD_ID, value = Dist.CLIENT)
public final class FloatingPanelEvents {
    private static boolean configLoaded;

    private FloatingPanelEvents() {
    }

    /** Ensures the subscriber class is initialized and layout is loaded once. */
    public static void init() {
        ensureConfigLoaded();
    }

    private static void ensureConfigLoaded() {
        if (configLoaded) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        FloatingPanelState state = FloatingPanelState.get();
        FloatingPanelConfig.loadInto(state);
        state.clampToScreen(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
        configLoaded = true;
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ensureConfigLoaded();
        ClientBackpackView.get().clear();
        FloatingPanelState state = FloatingPanelState.get();
        state.setVisible(false);
        state.endDrag();
        FloatingPanelConfig.loadInto(state);
        Minecraft minecraft = Minecraft.getInstance();
        state.clampToScreen(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof AbstractContainerScreen && Minecraft.getInstance().player != null) {
            PacketDistributor.sendToServer(new MergePanelCursorPayload());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        ensureConfigLoaded();
        FloatingPanelRenderer.render(
                event.getGuiGraphics(),
                FloatingPanelState.get(),
                (int) guiMouseX(minecraft),
                (int) guiMouseY(minecraft));
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        ensureConfigLoaded();
        FloatingPanelRenderer.render(
                event.getGuiGraphics(),
                FloatingPanelState.get(),
                event.getMouseX(),
                event.getMouseY());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() || !state.isDragging()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        state.updateDrag((int) guiMouseX(minecraft), (int) guiMouseY(minecraft));
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (!acceptingToggle()) {
            return;
        }
        if (FloatingBackpackKeys.TOGGLE.matches(event.getKey(), event.getScanCode())) {
            ensureConfigLoaded();
            FloatingPanelState.get().toggleVisible();
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        if (!FloatingPanelState.get().isVisible()) {
            return;
        }
        double mouseX = guiMouseX(minecraft);
        double mouseY = guiMouseY(minecraft);
        if (FloatingPanelInput.onMouseButton(event.getButton(), event.getAction(), mouseX, mouseY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (FloatingPanelInput.onMouseButton(
                event.getButton(), GLFW.GLFW_PRESS, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (FloatingPanelInput.onMouseButton(
                event.getButton(), GLFW.GLFW_RELEASE, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (FloatingPanelInput.onMouseDragged(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    private static boolean acceptingToggle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return false;
        }
        if (minecraft.getOverlay() != null) {
            return false;
        }
        return !(minecraft.screen instanceof ChatScreen);
    }

    private static double guiMouseX(Minecraft minecraft) {
        return minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth()
                / minecraft.getWindow().getScreenWidth();
    }

    private static double guiMouseY(Minecraft minecraft) {
        return minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight()
                / minecraft.getWindow().getScreenHeight();
    }
}
