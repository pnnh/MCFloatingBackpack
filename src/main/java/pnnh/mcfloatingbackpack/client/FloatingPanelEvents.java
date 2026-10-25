package pnnh.mcfloatingbackpack.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.network.BackpackOpenRequestPayload;
import pnnh.mcfloatingbackpack.network.MergePanelCursorPayload;

/**
 * Client overlay: in-world or over container GUIs. Hidden on pause/options/other menus.
 */
@EventBusSubscriber(modid = MCFloatingBackpack.MOD_ID, value = Dist.CLIENT)
public final class FloatingPanelEvents {
    private static boolean configLoaded;

    private FloatingPanelEvents() {
    }

    public static void init() {
        ensureConfigLoaded();
    }

    /**
     * Panel may show in the world HUD ({@code screen == null}) or over a container GUI.
     * Not over pause / options / other game menus.
     */
    public static boolean allowsPanel(Screen screen) {
        return screen == null || ContainerScreenCompat.isOpenContainerScreen(screen);
    }

    /** After server session open/close updates visibility. */
    public static void syncMouseToPanelVisibility() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null) {
            return;
        }
        FloatingPanelState state = FloatingPanelState.get();
        if (state.isVisible()) {
            if (minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.releaseMouse();
            }
        } else if (!minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }
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

    private static void closePanel() {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() && ClientBackpackView.get().getActiveRef() == null) {
            return;
        }
        state.setVisible(false);
        ClientBackpackView.get().clear();
        PacketDistributor.sendToServer(BackpackOpenRequestPayload.close());
        syncMouseToPanelVisibility();
    }

    private static void hideIfDisallowed(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        if (!allowsPanel(minecraft.screen) && FloatingPanelState.get().isVisible()) {
            closePanel();
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
        FloatingPanelState state = FloatingPanelState.get();
        Screen neu = event.getNewScreen();

        // ESC while panel open → close panel, do not open pause menu.
        if (state.isVisible() && neu instanceof PauseScreen) {
            event.setCanceled(true);
            closePanel();
            return;
        }

        if (ContainerScreenCompat.isOpenContainerScreen(neu)) {
            if (minecraft.player != null) {
                PacketDistributor.sendToServer(new MergePanelCursorPayload());
            }
            return;
        }

        if (neu != null && state.isVisible()) {
            closePanel();
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
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // When a screen is open, ScreenEvent.Render draws the panel instead.
        if (minecraft.screen != null) {
            return;
        }
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible()) {
            return;
        }
        ensureConfigLoaded();
        FloatingPanelRenderer.render(
                event.getGuiGraphics(),
                state,
                (int) guiMouseX(minecraft),
                (int) guiMouseY(minecraft));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        hideIfDisallowed(minecraft);
        FloatingPanelState state = FloatingPanelState.get();
        if (state.isVisible()) {
            state.setTotalRows(ClientBackpackView.get().rows());
            state.layout(minecraft.getWindow().getGuiScaledHeight());
            state.clampToScreen(
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight());
        }
        if (state.isVisible() && minecraft.screen == null) {
            syncMouseToPanelVisibility();
        }

        if (!state.isVisible() || (!state.isDragging() && !state.isScrolling())) {
            return;
        }
        if (!allowsPanel(minecraft.screen)) {
            return;
        }
        int mouseX = (int) guiMouseX(minecraft);
        int mouseY = (int) guiMouseY(minecraft);
        if (state.isDragging()) {
            state.updateDrag(mouseX, mouseY);
        } else if (state.isScrolling()) {
            state.updateScrollDrag(mouseY);
        }
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
            closePanel();
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
            closePanel();
            return;
        }
        if (!allowsPanel(minecraft.screen)) {
            return;
        }
        BackpackRef ref = BackpackRef.resolve(minecraft.player);
        if (ref == null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.mcfloatingbackpack.need_backpack"), true);
            return;
        }
        PacketDistributor.sendToServer(BackpackOpenRequestPayload.open(ref));
    }

    @SubscribeEvent
    public static void onWorldMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != null) {
            return;
        }
        if (scrollPanel(event.getScrollDeltaY(), guiMouseX(minecraft), guiMouseY(minecraft))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!ContainerScreenCompat.isOpenContainerScreen(event.getScreen())) {
            return;
        }
        if (scrollPanel(event.getScrollDeltaY(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    private static boolean scrollPanel(double scrollDeltaY, double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() || scrollDeltaY == 0 || !state.contains(mouseX, mouseY)) {
            return false;
        }
        state.scrollBy(scrollDeltaY > 0 ? -1 : 1);
        return true;
    }

    @SubscribeEvent
    public static void onWorldMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || !FloatingPanelState.get().isVisible()) {
            return;
        }
        if (FloatingPanelInput.onMouseButton(
                event.getButton(),
                event.getAction(),
                guiMouseX(minecraft),
                guiMouseY(minecraft))) {
            event.setCanceled(true);
        }
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
