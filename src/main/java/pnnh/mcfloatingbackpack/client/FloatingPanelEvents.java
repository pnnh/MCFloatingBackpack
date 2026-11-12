package pnnh.mcfloatingbackpack.client;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
import pnnh.mcfloatingbackpack.client.creative.CreativeCursorClient;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;
import pnnh.mcfloatingbackpack.client.panel.DeleteKeyInput;
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
import pnnh.mcfloatingbackpack.client.panel.pickup.PickupPanelInput;
import pnnh.mcfloatingbackpack.client.panel.pickup.PickupPanelState;
import pnnh.mcfloatingbackpack.client.panel.search.SearchInput;
import pnnh.mcfloatingbackpack.client.panel.search.SearchState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewInput;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelInput;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmInput;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmState;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashPanelInput;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashPanelState;
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
        SearchState.get().reset();
        PickupPanelState.get().collapse();
        TrashPanelState.get().collapse();
        TrashConfirmState.get().cancel();
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
        SearchState.get().reset();
        TagPanelState.get().reset();
        TagOverviewState.get().reset();
        PickupPanelState.get().reset();
        TrashPanelState.get().reset();
        TrashConfirmState.get().cancel();
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
        SearchState.get().reset();
        TagPanelState.get().reset();
        TagOverviewState.get().reset();
        PickupPanelState.get().reset();
        TrashPanelState.get().reset();
        TrashConfirmState.get().cancel();
        QuickTransferInput.reset();
        PanelDragInput.reset();
        PanelSelectionInput.reset();
        ClientBackpackView.get().clear();
        FloatingPanelState state = FloatingPanelState.get();
        state.setVisible(false);
        state.endDrag();
        state.endResize();
    }

    /** 027: the creative inventory closed; the server forgets its cursor mirror with the close packet. */
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen) {
            CreativeCursorClient.reset();
        }
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
            // 027: the creative inventory starts from an empty mirror; tell the server what is on the cursor before the merge.
            CreativeCursorClient.reset();
            CreativeCursorClient.flush(neu);
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
        traceMainHand();
    }

    /** Diagnostics: the client's own look at its main hand, the server has one too (see MainHandTrace). */
    private static final pnnh.mcfloatingbackpack.diag.MainHandTrace CLIENT_MAIN_HAND = new pnnh.mcfloatingbackpack.diag.MainHandTrace();

    private static void traceMainHand() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var change = CLIENT_MAIN_HAND.observe(
                minecraft.player.getUUID(), minecraft.player.getInventory().selected, minecraft.player.getMainHandItem());
        if (change != null) {
            pnnh.mcfloatingbackpack.diag.DiagLog.log(
                    pnnh.mcfloatingbackpack.diag.DiagLog.Side.CLIENT,
                    change.sameSlot() ? pnnh.mcfloatingbackpack.diag.LogLevel.WARN : pnnh.mcfloatingbackpack.diag.LogLevel.INFO,
                    "{}; panel visible: {}; backpacks now: {}",
                    change.text(),
                    FloatingPanelState.get().isVisible(),
                    pnnh.mcfloatingbackpack.diag.MainHandTrace.backpacks(minecraft.player.getInventory()));
        }
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
        // 031: the snapshot key (not bound until the player binds it): with no screen open the game counts its presses.
        while (FloatingBackpackKeys.SNAPSHOTS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                SnapshotClient.open();
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
        // 025: the overview goes back to the selection view when the panel is hidden or the tag manager has left the
        // function area (collapsing the tag panel by hand keeps it, so it is found as it was left).
        if ((!FloatingPanelState.get().isVisible() || !ClientBackpackView.get().hasTagManager())
                && !TagOverviewState.get().isPristine()) {
            TagOverviewState.get().resetView();
        }
        // 024: the pickup blacklist page needs an auto pickup in the left area (anywhere in its whole list) and an open panel.
        if (PickupPanelState.get().isOpen()
                && (!FloatingPanelState.get().isVisible() || !ClientBackpackView.get().hasAutoPickup())) {
            PickupPanelState.get().collapse();
        }
        // 022: a question needs the panel, the trash can and the screen it was asked on.
        if (TrashConfirmState.get().shouldDiscard(
                FloatingPanelState.get().isVisible(), ClientBackpackView.get().hasTrashCan(), minecraft.screen)) {
            TrashConfirmState.get().cancel();
        }
        // 021: the trash can panel needs a trash can in the right area (anywhere in its whole list) and an open panel.
        if (TrashPanelState.get().isOpen()
                && (!FloatingPanelState.get().isVisible() || !ClientBackpackView.get().hasTrashCan())) {
            TrashPanelState.get().collapse();
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
        // 027: before the screen sees E or ESC, so the cursor reaches the server ahead of the close packet.
        CreativeCursorClient.flush();
        // 022: with the confirmation dialog up ESC only withdraws the question. Cancelling the event means neither the
        // host screen (which would close the panel) nor a container screen (which would close itself) ever sees it.
        if (hostsPanel(event.getScreen())
                && event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE
                && TrashConfirmInput.onEscape()) {
            event.setCanceled(true);
            return;
        }
        // 042: the search box in the title bar takes the keyboard the same way (and Esc is its own: clear and close).
        if (hostsPanel(event.getScreen()) && SearchState.get().isFocused()) {
            SearchInput.onKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
            return;
        }
        if (hostsPanel(event.getScreen())
                && (TagPanelState.get().isEditing() || TagOverviewState.get().isFilterEditing())) {
            // 025: in the overview the keys go to its own text box (the filter box, or the detail page's add box).
            if (TagOverviewState.get().view() != TagOverviewState.View.SELECTION) {
                TagOverviewInput.onKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            } else {
                TagPanelInput.onKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            }
            event.setCanceled(true);
        }
        // 024: the pickup blacklist page's text box takes the keyboard the same way.
        if (hostsPanel(event.getScreen()) && PickupPanelState.get().isEditing()) {
            PickupPanelInput.onKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
        }
    }

    /**
     * 022: the delete key. {@code Post} fires only for a key the screen did not handle, so a search box with the focus
     * (JEI, the recipe book) has already taken a Backspace and it never gets here. The event is cancelled, which swallows
     * the key, only when the key was used; otherwise nothing is touched.
     */
    @SubscribeEvent
    public static void onScreenKeyPressedPost(ScreenEvent.KeyPressed.Post event) {
        if (!hostsPanel(event.getScreen())
                || !FloatingBackpackKeys.DELETE_TO_TRASH.isActiveAndMatches(
                        InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (DeleteKeyInput.onKey(event.getScreen(), guiMouseX(minecraft), guiMouseY(minecraft))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (hostsPanel(event.getScreen())
                && (TagPanelState.get().isEditing()
                        || PickupPanelState.get().isEditing()
                        || TagOverviewState.get().isFilterEditing()
                        || SearchState.get().isFocused())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        // 042: the search box takes every character, any language (a code point, not a char).
        if (hostsPanel(event.getScreen()) && SearchState.get().isFocused()) {
            SearchInput.onCharTyped(event.getCodePoint());
            event.setCanceled(true);
            return;
        }
        if (hostsPanel(event.getScreen())
                && (TagPanelState.get().isEditing() || TagOverviewState.get().isFilterEditing())) {
            if (TagOverviewState.get().view() != TagOverviewState.View.SELECTION) {
                TagOverviewInput.onCharTyped(event.getCodePoint());
            } else {
                TagPanelInput.onCharTyped(event.getCodePoint());
            }
            event.setCanceled(true);
        }
        if (hostsPanel(event.getScreen()) && PickupPanelState.get().isEditing()) {
            PickupPanelInput.onCharTyped(event.getCodePoint());
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
        // 022: with the confirmation dialog up the wheel does nothing anywhere, and is taken.
        if (state.isVisible() && scrollDeltaY != 0 && TrashConfirmState.get().isPending()) {
            return true;
        }
        // 021: the wheel over the trash can panel (outside the floating panel's rectangle) does nothing, but is taken.
        if (state.isVisible() && scrollDeltaY != 0 && TrashPanelInput.covers(mouseX, mouseY)) {
            return true;
        }
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
        // 024: so does the pickup blacklist page.
        if (area == PanelArea.MAIN && PickupPanelState.get().isOpen()) {
            PickupPanelInput.onMainScroll(scrollDeltaY);
            return true;
        }
        if (area != null) {
            state.scrollBy(area, scrollDeltaY > 0 ? -1 : 1);
        }
        return true;
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        // 027: the creative inventory's cursor, as it is before this click.
        CreativeCursorClient.flush();
        if (!hostsPanel(event.getScreen())) {
            return;
        }
        if (FloatingPanelInput.onMouseButton(
                event.getButton(), GLFW.GLFW_PRESS, event.getMouseX(), event.getMouseY())) {
            // 022: a click that lands on the panel is a click away from whatever text box has the focus. The screen and
            // the mods' widgets (JEI's search box, the recipe book's) learn of a click elsewhere from the very events
            // that are cancelled here, so without this the box would keep the keyboard. This is the screen's own
            // "click elsewhere" step and names no mod's type.
            event.getScreen().clearFocus();
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
        CreativeCursorClient.flush();
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
