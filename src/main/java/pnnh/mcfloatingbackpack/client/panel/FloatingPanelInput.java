package pnnh.mcfloatingbackpack.client.panel;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.creative.CreativeCursorClient;
import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;
import pnnh.mcfloatingbackpack.client.panel.pickup.PickupPanelInput;
import pnnh.mcfloatingbackpack.client.panel.pickup.PickupPanelState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelInput;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmInput;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashPanelInput;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashPanelState;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.ToolAreaRules;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.network.BackpackClickPayload;

/**
 * Hit-testing, panel drag, and slot clicks for the floating backpack.
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingPanelInput {
    private static final ConsumedPressTracker PRESSES = new ConsumedPressTracker();

    private FloatingPanelInput() {
    }

    /** A press taken outside {@link #onMouseButton} (quick transfer on a screen slot): swallow its release too. */
    public static void markPressConsumed(int button) {
        PRESSES.press(button);
    }

    /** Call when a mouse event arrives while the panel is hidden: forget any presses it had swallowed. */
    public static void resetPressTracking() {
        PRESSES.clear();
        TrashPanelInput.resetTracking();
    }

    /**
     * @return true if the event should be consumed (cancelled)
     */
    public static boolean onMouseButton(int button, int action, double mouseX, double mouseY) {
        if (!FloatingPanelState.get().isVisible()) {
            PRESSES.clear();
            TrashPanelInput.resetTracking();
            return false;
        }
        // 021: where a press went down decides whether its release on the trash can panel is a drop.
        if (action == GLFW.GLFW_PRESS) {
            TrashPanelInput.notePress(button, mouseX, mouseY);
        }
        boolean consumed = dispatchMouseButton(button, action, mouseX, mouseY);
        if (action == GLFW.GLFW_PRESS) {
            if (consumed) {
                PRESSES.press(button);
            }
            return consumed;
        }
        if (action == GLFW.GLFW_RELEASE) {
            // A release is swallowed only if its press was (or it ends a drag/scroll/resize, which
            // can only start from a swallowed press). Never because the cursor merely sits over the
            // panel: the panel can open between press and release, and swallowing that release
            // leaves the game thinking the button is still held.
            return PRESSES.release(button) || consumed;
        }
        return consumed;
    }

    private static boolean dispatchMouseButton(int button, int action, double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        boolean isRight = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        // 022: while a confirmation dialog is up every press, of any button, anywhere, goes to it first.
        if (state.isVisible() && action == GLFW.GLFW_PRESS && TrashConfirmInput.onPress(button, mouseX, mouseY)) {
            return true;
        }
        if (!state.isVisible() || (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && !isRight)) {
            return false;
        }

        if (action == GLFW.GLFW_PRESS) {
            // 018: a press anywhere but on the tag panel itself takes the focus away from its text box.
            if (TagPanelState.get().isEditing()
                    && !ClientBackpackView.get().layout().rect(PanelArea.MAIN).contains(mouseX, mouseY)) {
                TagPanelState.get().stopEditing();
            }
            // 025: and for the tag overview's filter box.
            if (TagOverviewState.get().isFilterEditing()
                    && !ClientBackpackView.get().layout().rect(PanelArea.MAIN).contains(mouseX, mouseY)) {
                TagOverviewState.get().stopFilterEditing();
            }
            // 024: and the same for the pickup blacklist page's text box.
            if (PickupPanelState.get().isEditing()
                    && !ClientBackpackView.get().layout().rect(PanelArea.MAIN).contains(mouseX, mouseY)) {
                PickupPanelState.get().stopEditing();
            }
            // 021: the trash can panel lies outside the floating panel's rectangle; a press on it is claimed here.
            if (TrashPanelInput.onPress(button, mouseX, mouseY)) {
                return true;
            }
            // Close button and resize handle sit on/inside the drag bar and body respectively, so
            // they must be checked before those broader hit tests claim the press first.
            // 031: the snapshot button, left of the close button, opens the snapshot window (the panel is hidden meanwhile, then back as it was).
            if (!isRight && state.hitsSnapshotButton(mouseX, mouseY)) {
                SnapshotClient.open();
                return true;
            }
            if (!isRight && state.hitsCloseButton(mouseX, mouseY)) {
                FloatingPanelEvents.closePanel();
                return true;
            }
            if (!isRight && state.hitsResizeHandle(mouseX, mouseY)) {
                state.beginResize((int) mouseX, (int) mouseY);
                return true;
            }
            // Dragging the panel stays left-button-only, but any press over the
            // panel must still be consumed so it can't leak through to whatever real screen sits
            // underneath the overlay.
            if (!isRight && state.hitsDragBar(mouseX, mouseY)) {
                state.beginDrag((int) mouseX, (int) mouseY);
                return true;
            }
            // 016: a press on a slot of one of the three tool areas. They only do plain clicks: a Shift or Ctrl
            // press is swallowed (Shift belongs to the main area's selection, Ctrl to its transfers), a press
            // while a main-area drag is under way does nothing, and otherwise the click goes out at once -
            // no deferred settling, no selection change, no quick transfer.
            PanelLayout.Hit toolHit = ClientBackpackView.get().layout().hit(mouseX, mouseY);
            if (toolHit != null && toolHit.area().isTool()) {
                if (Screen.hasShiftDown() || Screen.hasControlDown() || PanelDragInput.isActive()) {
                    return true;
                }
                // 018: a plain left click with an empty cursor on a tag manager in the function area opens or closes
                // the tag panel instead of picking it up; the right button still picks it up. Anywhere else it is an
                // ordinary item.
                // 021: the same for a trash can in the right area, which opens or closes the trash can panel.
                if (!isRight
                        && toolHit.area() == PanelArea.RIGHT
                        && FloatingPanelRenderer.cursorStack(Minecraft.getInstance()).isEmpty()
                        && ClientBackpackView.get().getToolStack(toolHit.area(), toolHit.index())
                                .is(ModItems.TRASH_CAN.get())) {
                    TrashPanelState.get().toggle();
                    return true;
                }
                if (!isRight
                        && toolHit.area() == PanelArea.BOTTOM
                        && FloatingPanelRenderer.cursorStack(Minecraft.getInstance()).isEmpty()
                        && ClientBackpackView.get().getToolStack(toolHit.area(), toolHit.index())
                                .is(ModItems.TAG_MANAGER.get())) {
                    TagPanelState.get().toggle();
                    return true;
                }
                // 024: a plain left click with an empty cursor on an auto pickup in the left area opens or closes the
                // blacklist page instead of picking it up; the right button still picks it up.
                if (!isRight
                        && toolHit.area() == PanelArea.LEFT
                        && FloatingPanelRenderer.cursorStack(Minecraft.getInstance()).isEmpty()
                        && ClientBackpackView.get().getToolStack(toolHit.area(), toolHit.index())
                                .is(ModItems.AUTO_PICKUP.get())) {
                    PickupPanelState.get().toggle();
                    return true;
                }
                // 021: the right area takes one trash can and the bottom area one tag manager. The server decides; this
                // only spares the round trip and says why nothing happened.
                if (refusedByLimit(toolHit.area(), toolHit.index())) {
                    return true;
                }
                sendClick(toolHit.area(), toolHit.index(), isRight);
                return true;
            }
            // 018: while the tag panel covers the main area it takes every press there, and the main area's own
            // gestures (selection, quick transfer, drag) never see it.
            if (TagPanelState.get().isOpen()
                    && ClientBackpackView.get().layout().rect(PanelArea.MAIN).contains(mouseX, mouseY)) {
                return TagPanelInput.onMainPress(button, mouseX, mouseY);
            }
            // 024: the same for the pickup blacklist page.
            if (PickupPanelState.get().isOpen()
                    && ClientBackpackView.get().layout().rect(PanelArea.MAIN).contains(mouseX, mouseY)) {
                return PickupPanelInput.onMainPress(button, mouseX, mouseY);
            }
            int slot = PanelSlots.indexAt(state, mouseX, mouseY);
            // The last reflowed row can run past the backpack's real slot count once visibleCols
            // no longer evenly divides its capacity; indexAt only knows the virtual grid, so the
            // real bound has to be checked here before trusting the index.
            if (slot >= 0 && slot < ClientBackpackView.get().slotCount()) {
                // 014: Shift selects slots, and any ordinary click clears the selection first. Runs before
                // everything else so a Shift press never reaches the click paths below.
                if (PanelSelectionInput.beginFromPanel(slot, isRight, mouseX, mouseY)) {
                    return true;
                }
                // 011: Ctrl+left-click/drag moves stacks to the player's inventory. Works with any
                // screen open (or none): the target is the real Inventory, not a third-party menu.
                if (!isRight && QuickTransferInput.beginFromPanel(slot, mouseX, mouseY)) {
                    return true;
                }
                // 013: a second button pressed while a drag is under way does nothing (and is swallowed).
                if (PanelDragInput.isActive()) {
                    return true;
                }
                // 013: with an item on the cursor the click waits for the release, so dragging over
                // several slots can distribute it; with an empty cursor the pickup happens at once and
                // dragging on collects same-kind items.
                if (PanelDragInput.beginFromPanel(slot, isRight, mouseX, mouseY)) {
                    return true;
                }
                sendClick(slot, isRight);
                return true;
            }
            if (state.hitsBody(mouseX, mouseY) || state.contains(mouseX, mouseY)) {
                return true;
            }
            return false;
        }

        // 021: a drag that ends on the trash can panel drops the cursor stack into it; no other gesture settles it. The
        // release is swallowed only if its press was (ours); a press that went down on a container screen's slot is left
        // to the game, which finishes its own drag with the now empty cursor.
        if (action == GLFW.GLFW_RELEASE && TrashPanelInput.onRelease(button, mouseX, mouseY)) {
            return PRESSES.has(button);
        }

        if (action == GLFW.GLFW_RELEASE && PanelSelectionInput.onRelease(button, mouseX, mouseY)) {
            return true;
        }

        if (action == GLFW.GLFW_RELEASE && PanelDragInput.onRelease(button, mouseX, mouseY)) {
            return true;
        }

        if (action == GLFW.GLFW_RELEASE && state.isDragging()) {
            finishDrag(state);
            return true;
        }

        if (action == GLFW.GLFW_RELEASE && state.isResizing()) {
            finishResize(state);
            return true;
        }

        return false;
    }

    public static boolean onMouseDragged(double mouseX, double mouseY) {
        FloatingPanelState state = FloatingPanelState.get();
        if (!state.isVisible()) {
            return false;
        }
        if (state.isDragging()) {
            state.updateDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (state.isResizing()) {
            state.updateResize((int) mouseX, (int) mouseY);
            return true;
        }
        return false;
    }

    /**
     * 021/024: whether putting what the player holds into this tool-area slot would break the one-per-area limit; if so
     * the player is told in the chat. Reads the same stack the server will (the cursor, or with Alt and no container
     * screen the main-hand item).
     */
    private static boolean refusedByLimit(PanelArea area, int slotIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen);
        ItemStack held = !screenOpen && Screen.hasAltDown() && minecraft.player != null
                ? minecraft.player.getMainHandItem()
                : FloatingPanelRenderer.cursorStack(minecraft);
        if (held.isEmpty()
                || ToolAreaRules.canPlace(
                        area,
                        held,
                        slotIndex,
                        ClientBackpackView.get().toolStacks(area),
                        stack -> stack.is(ModItems.TRASH_CAN.get()),
                        stack -> stack.is(ModItems.TAG_MANAGER.get()),
                        stack -> stack.is(ModItems.AUTO_PICKUP.get()))) {
            return false;
        }
        String key = held.is(ModItems.TRASH_CAN.get())
                ? "message.mcfloatingbackpack.limit.trash_can"
                : held.is(ModItems.AUTO_PICKUP.get())
                        ? "message.mcfloatingbackpack.limit.auto_pickup"
                        : "message.mcfloatingbackpack.limit.tag_manager";
        FloatingPanelEvents.chat(minecraft, Component.translatable(key));
        return true;
    }

    static void sendClick(int slotIndex, boolean rightClick) {
        sendClick(PanelArea.MAIN, slotIndex, rightClick);
    }

    /**
     * 037: whether what the player holds cannot go into this cell of the main area because it is in a category extension row that
     * takes something else. Reads the same stack the server will (the cursor, or with Alt and no container screen the main-hand
     * item). Taking out is never refused. Only the server decides; this spares a request that is certain to change nothing, and the
     * guess is generous when it cannot be sure (see {@link ClientPlacement}).
     */
    private static boolean refusedByRow(int slotIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen);
        ItemStack held = !screenOpen && Screen.hasAltDown() && minecraft.player != null
                ? minecraft.player.getMainHandItem()
                : FloatingPanelRenderer.cursorStack(minecraft);
        return !held.isEmpty() && !ClientPlacement.canPlace(ClientBackpackView.get(), slotIndex, held);
    }

    static void sendClick(PanelArea area, int slotIndex, boolean rightClick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (area == PanelArea.MAIN && refusedByRow(slotIndex)) {
            return;
        }
        BackpackRef ref = ClientBackpackView.get().getActiveRef();
        if (ref == null) {
            ref = BackpackRef.resolve(minecraft.player);
        }
        if (ref == null) {
            stateClose();
            return;
        }
        // 014: Alt (Option on macOS) swaps with the main-hand item; Shift is the selection key.
        boolean swapHand = Screen.hasAltDown();
        boolean screenOpen = ContainerScreenCompat.isOpenContainerScreen(minecraft.screen);
        // 027: the server must know the creative cursor before it handles this click.
        CreativeCursorClient.flush();
        PacketDistributor.sendToServer(
                new BackpackClickPayload(
                        area.toByte(),
                        slotIndex,
                        swapHand,
                        rightClick,
                        screenOpen,
                        true,
                        ref.source().ordinal(),
                        ref.invIndex()));
    }

    private static void stateClose() {
        FloatingPanelState.get().setVisible(false);
        ClientBackpackView.get().clear();
    }

    private static void finishDrag(FloatingPanelState state) {
        Minecraft minecraft = Minecraft.getInstance();
        state.endDrag();
        state.clampToScreen(
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
        FloatingPanelConfig.saveFrom(state);
    }

    private static void finishResize(FloatingPanelState state) {
        state.endResize();
        FloatingPanelConfig.saveFrom(state);
    }
}
