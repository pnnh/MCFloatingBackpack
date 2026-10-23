package pnnh.mcfloatingbackpack.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelConfig;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelInput;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.network.MergePanelCursorPayload;

/**
 * Client overlay: render, toggle, and mouse routing for the floating panel.
 * Never opens a Screen for the panel itself.
 * Visible only while a container screen is open (inventory/chest/backpack GUIs).
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

    /**
     * Panel is useless without a free mouse cursor on a container GUI.
     * Force-hide during free play, pause menu, chat, and any non-container screen.
     */
    private static void hideUnlessContainerOpen(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        if (!ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)) {
            FloatingPanelState.get().setVisible(false);
        }
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
        Minecraft minecraft = Minecraft.getInstance();
        if (ContainerScreenCompat.isOpenContainerScreen(event.getNewScreen())) {
            if (minecraft.player != null) {
                PacketDistributor.sendToServer(new MergePanelCursorPayload());
            }
        } else {
            FloatingPanelState.get().setVisible(false);
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (ContainerScreenCompat.isOpenContainerScreen(event.getScreen())) {
            FloatingPanelState.get().setVisible(false);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!ContainerScreenCompat.isOpenContainerScreen(event.getScreen())) {
            return;
        }
        ensureConfigLoaded();
        FloatingPanelRenderer.render(
                event.getGuiGraphics(),
                FloatingPanelState.get(),
                event.getMouseX(),
                event.getMouseY());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        hideUnlessContainerOpen(minecraft);

        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() || !state.isDragging()) {
            return;
        }
        if (!ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)) {
            return;
        }
        state.updateDrag((int) guiMouseX(minecraft), (int) guiMouseY(minecraft));
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.getOverlay() != null) {
            return;
        }

        FloatingPanelState state = FloatingPanelState.get();
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && state.isVisible()) {
            state.setVisible(false);
            // Do not cancel: vanilla may still close the container / open the pause menu.
            return;
        }

        if (minecraft.screen instanceof ChatScreen) {
            return;
        }
        if (!FloatingBackpackKeys.TOGGLE.matches(event.getKey(), event.getScanCode())) {
            return;
        }

        ensureConfigLoaded();
        if (state.isVisible()) {
            state.setVisible(false);
        } else if (ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)) {
            state.setVisible(true);
        }
        // No container screen → skip open (mouse is captured / panel unusable).
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!ContainerScreenCompat.isOpenContainerScreen(event.getScreen())) {
            return;
        }
        if (FloatingPanelInput.onMouseButton(
                event.getButton(), GLFW.GLFW_PRESS, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!ContainerScreenCompat.isOpenContainerScreen(event.getScreen())) {
            return;
        }
        if (FloatingPanelInput.onMouseButton(
                event.getButton(), GLFW.GLFW_RELEASE, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!ContainerScreenCompat.isOpenContainerScreen(event.getScreen())) {
            return;
        }
        if (FloatingPanelInput.onMouseDragged(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
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
