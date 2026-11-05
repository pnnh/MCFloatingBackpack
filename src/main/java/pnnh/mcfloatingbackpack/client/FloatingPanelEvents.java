package pnnh.mcfloatingbackpack.client;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelConfig;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelInput;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.HostScreenManager;
import pnnh.mcfloatingbackpack.client.panel.PanelDragInput;
import pnnh.mcfloatingbackpack.client.panel.PanelHostScreen;
import pnnh.mcfloatingbackpack.client.panel.PanelSelection;
import pnnh.mcfloatingbackpack.client.panel.PanelSelectionInput;
import pnnh.mcfloatingbackpack.client.panel.QuickTransferInput;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelInput;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;
import pnnh.mcfloatingbackpack.compat.AttachNotice;
import pnnh.mcfloatingbackpack.compat.ConfigNotices;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.network.BackpackOpenRequestPayload;
import pnnh.mcfloatingbackpack.network.MergePanelCursorPayload;

/**
 * Client overlay: over container GUIs, or over the transparent host screen (019) when no other screen is open.
 * Hidden on pause/options/other menus.
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
     * The screens the panel is drawn on and takes the mouse from: its own host screen (019) or a container GUI.
     * On any other screen (pause, options, chat...) the panel is hidden, not closed.
     */
    public static boolean hostsPanel(Screen screen) {
        return screen instanceof PanelHostScreen || ContainerScreenCompat.isOpenContainerScreen(screen);
    }

    /** The panel may be opened with its hotkey with no screen open, or over a screen that hosts it. */
    public static boolean allowsPanel(Screen screen) {
        return screen == null || hostsPanel(screen);
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

    /** Also called by {@code FloatingPanelInput} when the panel's own close button is clicked. */
    public static void closePanel() {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible() && ClientBackpackView.get().getActiveRef() == null) {
            return;
        }
        state.setVisible(false);
        ClientBackpackView.get().clear();
        PacketDistributor.sendToServer(BackpackOpenRequestPayload.close());
        HostScreenManager.ensure();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ensureConfigLoaded();
        QuickTransferInput.reset();
        PanelDragInput.reset();
        PanelSelectionInput.reset();
        TagPanelState.get().reset();
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

    private static void finishPanelMove(Minecraft minecraft, FloatingPanelState state) {
        state.endDrag();
        state.clampToScreen(
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        FloatingPanelConfig.saveFrom(state);
    }

    /** The visible state does not survive leaving the world (019: other screens only hide the panel). */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HostScreenManager.reset();
        TagPanelState.get().reset();
        QuickTransferInput.reset();
        PanelDragInput.reset();
        PanelSelectionInput.reset();
        ClientBackpackView.get().clear();
        FloatingPanelState state = FloatingPanelState.get();
        state.setVisible(false);
        state.endDrag();
        state.endResize();
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen neu = event.getNewScreen();

        // 019: the host is the panel's own screen, never a reason to close it.
        if (neu instanceof PanelHostScreen) {
            return;
        }

        // A container screen takes over the panel's cursor stack. Any other screen only hides the panel (019):
        // it is not drawn or clicked there, and its visible state is kept for when the screen closes.
        if (ContainerScreenCompat.isOpenContainerScreen(neu) && minecraft.player != null) {
            PacketDistributor.sendToServer(new MergePanelCursorPayload());
            if (neu instanceof AbstractContainerScreen<?> container) {
                // 020: say once, in the log, which mod id owns this screen (for the compat.modLevels config).
                AttachNotice.onAttach(MenuOwner.ownerOf(container.getMenu()));
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!hostsPanel(event.getScreen())) {
            return;
        }
        // 011/013/014: this hook is the only per-frame driver of the panel's gestures, so each runs exactly once
        // per frame.
        QuickTransferInput.tick(Minecraft.getInstance(), event.getMouseX(), event.getMouseY());
        PanelDragInput.tick(Minecraft.getInstance(), event.getMouseX(), event.getMouseY());
        PanelSelectionInput.tick(Minecraft.getInstance(), event.getMouseX(), event.getMouseY());
        ensureConfigLoaded();
        FloatingPanelRenderer.render(
                event.getGuiGraphics(),
                FloatingPanelState.get(),
                event.getMouseX(),
                event.getMouseY());
    }

    /**
     * 019: puts the host screen back the moment it is due, before the frame is drawn, so a screen closed from an
     * input callback never leaves a frame without the panel.
     */
    @SubscribeEvent
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        HostScreenManager.ensure();
    }

    /**
     * While the host is away replaying a key (a tick or so, with no screen at all) the panel would blink out. It is
     * drawn here for that window only - drawn, not clickable: there is no input path without a screen, and the host
     * is back before anyone could use it.
     */
    @SubscribeEvent
    public static void onRenderGuiWhileAway(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        FloatingPanelState state = FloatingPanelState.get();
        if (minecraft.screen != null || !state.isVisible() || !HostScreenManager.isAway()) {
            return;
        }
        ensureConfigLoaded();
        FloatingPanelRenderer.render(
                event.getGuiGraphics(), state, (int) guiMouseX(minecraft), (int) guiMouseY(minecraft));
    }

    /**
     * Last at the start of a tick, so every other mod has had its start-of-tick handler (some read their keys
     * there) before a key the host replayed is dropped and the host comes back.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTickStartLast(ClientTickEvent.Pre event) {
        HostScreenManager.beforeTick();
    }

    /** Last at the end of a tick, so every other mod has had its tick (and may have opened a screen). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTickLast(ClientTickEvent.Post event) {
        HostScreenManager.afterTick();
    }

    /** A line in this player's own chat, led by the mod's name; nothing is sent to the server or anyone else. */
    public static void chat(Minecraft minecraft, Component message) {
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        minecraft.gui.getChat().addMessage(
                Component.translatable("message.mcfloatingbackpack.prefix")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(" "))
                        .append(message.copy().withStyle(ChatFormatting.YELLOW)));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // 020: problems with the player's config are shown in the chat once the player is in a world.
        if (minecraft.player != null && ConfigNotices.hasPending()) {
            for (ConfigNotices.Notice notice : ConfigNotices.drain()) {
                chat(minecraft, Component.translatable(notice.key(), notice.arg()));
            }
        }
        QuickTransferInput.stopIfInvalid(minecraft);
        PanelDragInput.stopIfInvalid(minecraft);
        PanelSelectionInput.stopIfInvalid(minecraft);
        syncSelectionContext();
        // 018: the tag panel needs a tag manager in the function area and an open backpack panel.
        if (TagPanelState.get().isOpen()
                && (!FloatingPanelState.get().isVisible() || !ClientBackpackView.get().hasTagManager())) {
            TagPanelState.get().collapse();
        }
        FloatingPanelState state = FloatingPanelState.get();
        if (state.isVisible()) {
            state.setTotalRows(ClientBackpackView.get().rows());
            state.layout(
                    minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
            state.clampToScreen(
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight());
        }
        updateResizeCursor(minecraft, state, guiMouseX(minecraft), guiMouseY(minecraft));

        if (!state.isVisible() || (!state.isDragging() && !state.isResizing())) {
            return;
        }
        if (!hostsPanel(minecraft.screen)) {
            // A screen that does not host the panel (or a moment with none) took over mid-gesture: end it, so the
            // panel is not left stuck to the mouse.
            if (state.isDragging()) {
                finishPanelMove(minecraft, state);
            } else {
                state.endResize();
                FloatingPanelConfig.saveFrom(state);
            }
            return;
        }
        int mouseX = (int) guiMouseX(minecraft);
        int mouseY = (int) guiMouseY(minecraft);
        if (state.isDragging()) {
            state.updateDrag(mouseX, mouseY);
        } else if (state.isResizing()) {
            state.updateResize(mouseX, mouseY);
        }
    }

    /**
     * Swaps in the diagonal-resize system cursor while hovering (or dragging) the resize handle.
     * {@code glfwCreateStandardCursor} can return {@code NULL} on platforms that don't ship that
     * shape (observed on macOS); when it does, the default arrow cursor is left alone and the
     * resize drag itself still works, it just won't get the visual hint.
     */
    private static long resizeCursorHandle = MemoryUtil.NULL;
    private static boolean resizeCursorActive;

    private static void updateResizeCursor(
            Minecraft minecraft, FloatingPanelState state, double mouseX, double mouseY) {
        boolean shouldShow =
                state.isVisible() && (state.isResizing() || state.hitsResizeHandle(mouseX, mouseY));
        if (shouldShow == resizeCursorActive) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        if (shouldShow) {
            if (resizeCursorHandle == MemoryUtil.NULL) {
                resizeCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
            }
            if (resizeCursorHandle == MemoryUtil.NULL) {
                // Unsupported on this platform; nothing to apply, nothing to restore later.
                return;
            }
            GLFW.glfwSetCursor(window, resizeCursorHandle);
        } else {
            GLFW.glfwSetCursor(window, MemoryUtil.NULL);
        }
        resizeCursorActive = shouldShow;
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
            // 020: a container screen of a mod switched off in the config: say why nothing happened.
            if (minecraft.screen instanceof AbstractContainerScreen<?> container) {
                // In the chat (only this player sees it, and T shows it again), not the action bar that fades at once.
                chat(minecraft, Component.translatable(
                        "message.mcfloatingbackpack.panel_off", MenuOwner.ownerOf(container.getMenu())));
            }
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

    // 018: while the tag text box has the focus the keyboard is the box's. These run before the screen (and the host
    // screen's key forwarding) sees the event, and cancelling them also keeps InputEvent.Key from firing. Without the
    // focus nothing is cancelled.
    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (hostsPanel(event.getScreen()) && TagPanelState.get().isEditing()) {
            TagPanelInput.onKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (hostsPanel(event.getScreen()) && TagPanelState.get().isEditing()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (hostsPanel(event.getScreen()) && TagPanelState.get().isEditing()) {
            TagPanelInput.onCharTyped(event.getCodePoint());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!hostsPanel(event.getScreen())) {
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
        // 016: the wheel scrolls the area under the mouse and nothing else. Wheel up goes toward the start
        // (up, or left for the bottom row). Over the title bar, the padding or an area that has nothing to
        // scroll it does nothing, but the event is still taken so it cannot leak to the screen underneath.
        PanelArea area = ClientBackpackView.get().layout().areaAt(mouseX, mouseY);
        // 018: the expanded tag panel covers the main area and takes the wheel there.
        if (area == PanelArea.MAIN && TagPanelState.get().isOpen()) {
            TagPanelInput.onMainScroll(scrollDeltaY);
            return true;
        }
        if (area != null) {
            state.scrollBy(area, scrollDeltaY > 0 ? -1 : 1);
        }
        return true;
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!hostsPanel(event.getScreen())) {
            return;
        }
        if (FloatingPanelInput.onMouseButton(
                event.getButton(), GLFW.GLFW_PRESS, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        // 011: Ctrl+left-click on a player-inventory slot of a vanilla screen. Cancelling the press keeps
        // vanilla from picking the stack up (it only looks at Shift, never Ctrl); the release is swallowed
        // through the same tracker as other swallowed presses so no key state is left stuck.
        if (QuickTransferInput.beginFromScreen(
                event.getScreen(), event.getButton(), event.getMouseX(), event.getMouseY())) {
            FloatingPanelInput.markPressConsumed(event.getButton());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!hostsPanel(event.getScreen())) {
            return;
        }
        if (FloatingPanelInput.onMouseButton(
                event.getButton(), GLFW.GLFW_RELEASE, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!hostsPanel(event.getScreen())) {
            return;
        }
        if (FloatingPanelInput.onMouseDragged(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    /**
     * 014: the selection is by slot index, so it must not outlive the backpack it was made in. One place
     * covers every way the panel can go away (close button, ESC, screen change, a different backpack).
     */
    private static void syncSelectionContext() {
        BackpackRef ref = ClientBackpackView.get().getActiveRef();
        String refKey = ref == null ? null : ref.source().ordinal() + ":" + ref.invIndex();
        PanelSelection.get().syncContext(
                FloatingPanelState.get().isVisible(), refKey, ClientBackpackView.get().slotCount());
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
