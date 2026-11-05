package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.panel.QuickTransferGesture.Hit;
import pnnh.mcfloatingbackpack.client.panel.QuickTransferGesture.Side;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest.Direction;
import pnnh.mcfloatingbackpack.network.BackpackQuickTransferPayload;
import pnnh.mcfloatingbackpack.network.BackpackTakeFromContainerPayload;

/**
 * The part of the Ctrl quick-transfer gesture (011, 012) that talks to the game: finding the slot under
 * the cursor, sending requests, and driving a drag once per frame. The rules live in
 * {@link QuickTransferGesture} and {@link QuickTransferRules}.
 *
 * <p>"Ctrl" is {@link Screen#hasControlDown()}: Ctrl on Windows/Linux, Command on macOS (vanilla remaps
 * physical Ctrl+left-click to right-click there). When Shift is also held the gesture <em>yields</em>:
 * Ctrl+Shift belongs to vanilla and to mods' own shortcuts. This is the only place Shift is read, and
 * only to step aside.
 */
@OnlyIn(Dist.CLIENT)
public final class QuickTransferInput {
    private static final QuickTransferGesture GESTURE = new QuickTransferGesture();
    /** The screen (the host screen or a container screen) the gesture started on; a drag ends when it changes. */
    private static Screen activeScreen;

    private QuickTransferInput() {
    }

    /** Ctrl+left-click on a slot of the floating panel. @return true if the press was taken. */
    public static boolean beginFromPanel(int backpackIndex, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Screen.hasControlDown() || Screen.hasShiftDown() || !cursorIsEmpty(minecraft.screen)) {
            return false;
        }
        boolean hasItem = !ClientBackpackView.get().getStackInSlot(backpackIndex).isEmpty();
        start(minecraft, new Hit(Side.PANEL, backpackIndex, hasItem), mouseX, mouseY);
        return true;
    }

    /**
     * Ctrl+left-click on a slot of a container screen while the panel is visible: a slot of the player's
     * own inventory, or (when a container is open) one of the container's own slots. Anything else
     * (creative inventory, armor/offhand/crafting slots, output-style slots, an item on the cursor, Shift
     * held) is left to the game. @return true if the press was taken.
     */
    public static boolean beginFromScreen(Screen screen, int button, double mouseX, double mouseY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasControlDown() || Screen.hasShiftDown()) {
            return false;
        }
        if (!FloatingPanelState.get().isVisible() || !supports(screen) || !cursorIsEmpty(screen)) {
            return false;
        }
        Hit hit = screenSlotAt((AbstractContainerScreen<?>) screen, mouseX, mouseY);
        if (hit == null) {
            return false;
        }
        start(Minecraft.getInstance(), hit, mouseX, mouseY);
        return true;
    }

    private static void start(Minecraft minecraft, Hit hit, double mouseX, double mouseY) {
        activeScreen = minecraft.screen;
        send(GESTURE.begin(hit, mouseX, mouseY));
    }

    /** Per-frame driver; call with the current GUI-scaled mouse position. */
    public static void tick(Minecraft minecraft, double mouseX, double mouseY) {
        if (!GESTURE.isActive()) {
            return;
        }
        if (invalid(minecraft) || !Screen.hasControlDown() || Screen.hasShiftDown()) {
            GESTURE.stop();
            return;
        }
        boolean leftDown = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
        // One last step on the frame the button comes up, so the slot under the release point counts.
        send(GESTURE.advance(QuickTransferInput::resolveAt, mouseX, mouseY));
        if (!leftDown) {
            GESTURE.stop();
        }
    }

    /** Ends a drag whose context is gone (panel hidden, screen changed, item picked up); called every client tick. */
    public static void stopIfInvalid(Minecraft minecraft) {
        if (GESTURE.isActive() && invalid(minecraft)) {
            GESTURE.stop();
        }
    }

    public static void reset() {
        GESTURE.stop();
        activeScreen = null;
    }

    private static boolean invalid(Minecraft minecraft) {
        return minecraft.player == null
                || !FloatingPanelState.get().isVisible()
                || minecraft.screen != activeScreen
                || !cursorIsEmpty(minecraft.screen);
    }

    private static boolean cursorIsEmpty(Screen screen) {
        return !(screen instanceof AbstractContainerScreen<?> container) || container.getMenu().getCarried().isEmpty();
    }

    private static boolean supports(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>
                && QuickTransferRules.isInterceptableScreenClass(screen.getClass().getName());
    }

    private static Hit resolveAt(double x, double y) {
        FloatingPanelState state = FloatingPanelState.get();
        ClientBackpackView view = ClientBackpackView.get();
        if (state.contains(x, y)) {
            // Over the panel only its own slots count; the panel covers whatever is underneath (FR-015).
            int index = PanelSlots.indexAt(state, x, y);
            if (index < 0 || index >= view.slotCount()) {
                return null;
            }
            return new Hit(Side.PANEL, index, !view.getStackInSlot(index).isEmpty());
        }
        Screen screen = Minecraft.getInstance().screen;
        return supports(screen) ? screenSlotAt((AbstractContainerScreen<?>) screen, x, y) : null;
    }

    /**
     * The slot of the screen under the cursor that the gesture may act on, or null. Own hit test (not
     * {@code hoveredSlot}, which is only refreshed at render time and would miss slots a fast drag passes
     * over between frames). The first slot geometrically under the cursor decides: if it is not a valid
     * source (armor, an output slot, ...) the answer is null, never a neighbour.
     */
    private static Hit screenSlotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            int left = screen.getGuiLeft() + slot.x;
            int top = screen.getGuiTop() + slot.y;
            if (mouseX < left || mouseX >= left + 16 || mouseY < top || mouseY >= top + 16) {
                continue;
            }
            boolean own = slot.container == minecraft.player.getInventory();
            if (own) {
                return QuickTransferRules.isTransferableInventorySlot(true, slot.getContainerSlot(), true)
                        ? new Hit(Side.INVENTORY, slot.getContainerSlot(), slot.hasItem())
                        : null;
            }
            // A container's own slots only count while a container is open, not on the player's own inventory screen.
            if (screen.getMenu() == minecraft.player.inventoryMenu) {
                return null;
            }
            return QuickTransferRules.isContainerSourceSlot(
                            false, true, slot.mayPickup(minecraft.player), slot.mayPlace(slot.getItem()))
                    ? new Hit(Side.CONTAINER, slot.index, slot.hasItem())
                    : null;
        }
        return null;
    }

    /** Splits hits by where they live and sends one request per kind of source (at most 3 per frame). */
    private static void send(List<Hit> hits) {
        if (hits.isEmpty()) {
            return;
        }
        List<Integer> inventory = new ArrayList<>();
        List<Integer> container = new ArrayList<>();
        List<Integer> panel = new ArrayList<>();
        for (Hit hit : hits) {
            switch (hit.side()) {
                case INVENTORY -> inventory.add(hit.index());
                case CONTAINER -> container.add(hit.index());
                case PANEL -> panel.add(hit.index());
            }
        }
        if (!inventory.isEmpty()) {
            PacketDistributor.sendToServer(BackpackQuickTransferPayload.of(Direction.TO_BACKPACK, toArray(inventory)));
        }
        if (!container.isEmpty() && activeScreen instanceof AbstractContainerScreen<?> screen) {
            PacketDistributor.sendToServer(
                    new BackpackTakeFromContainerPayload(screen.getMenu().containerId, toArray(container)));
        }
        if (!panel.isEmpty()) {
            PacketDistributor.sendToServer(BackpackQuickTransferPayload.of(Direction.TO_INVENTORY, toArray(panel)));
        }
    }

    private static int[] toArray(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).toArray();
    }
}
