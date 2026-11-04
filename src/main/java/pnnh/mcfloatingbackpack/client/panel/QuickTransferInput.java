package pnnh.mcfloatingbackpack.client.panel;

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

/**
 * The part of the Ctrl quick-transfer gesture (011) that talks to the game: finding the slot under
 * the cursor, sending requests, and driving a drag once per frame. The rules live in
 * {@link QuickTransferGesture} and {@link QuickTransferRules}.
 *
 * <p>"Ctrl" is {@link Screen#hasControlDown()}: Ctrl on Windows/Linux, Command on macOS (vanilla
 * remaps physical Ctrl+left-click to right-click there). Shift is never read here.
 */
@OnlyIn(Dist.CLIENT)
public final class QuickTransferInput {
    private static final QuickTransferGesture GESTURE = new QuickTransferGesture();
    /** The screen (null = HUD) the gesture started on; a drag ends when it changes. */
    private static Screen activeScreen;

    private QuickTransferInput() {
    }

    /** Ctrl+left-click on a slot of the floating panel. @return true if the press was taken. */
    public static boolean beginFromPanel(int backpackIndex, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Screen.hasControlDown() || !cursorIsEmpty(minecraft.screen)) {
            return false;
        }
        boolean hasItem = !ClientBackpackView.get().getStackInSlot(backpackIndex).isEmpty();
        start(minecraft, new Hit(Side.PANEL, backpackIndex, hasItem), mouseX, mouseY);
        return true;
    }

    /**
     * Ctrl+left-click on a player-inventory slot of a vanilla container screen while the panel is
     * visible. Anything else (third-party screens, creative inventory, chest cells, armor/offhand,
     * crafting slots, an item on the cursor) is left to the game. @return true if the press was taken.
     */
    public static boolean beginFromScreen(Screen screen, int button, double mouseX, double mouseY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasControlDown()) {
            return false;
        }
        if (!FloatingPanelState.get().isVisible() || !supports(screen) || !cursorIsEmpty(screen)) {
            return false;
        }
        Slot slot = findInventorySlot((AbstractContainerScreen<?>) screen, mouseX, mouseY);
        if (slot == null) {
            return false;
        }
        start(Minecraft.getInstance(), new Hit(Side.INVENTORY, slot.getContainerSlot(), slot.hasItem()), mouseX, mouseY);
        return true;
    }

    private static void start(Minecraft minecraft, Hit hit, double mouseX, double mouseY) {
        activeScreen = minecraft.screen;
        List<Integer> now = GESTURE.begin(hit, mouseX, mouseY);
        send(GESTURE.side(), now);
    }

    /** Per-frame driver; call with the current GUI-scaled mouse position. */
    public static void tick(Minecraft minecraft, double mouseX, double mouseY) {
        if (!GESTURE.isActive()) {
            return;
        }
        if (invalid(minecraft) || !Screen.hasControlDown()) {
            GESTURE.stop();
            return;
        }
        boolean leftDown = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
        // One last step on the frame the button comes up, so the slot under the release point counts.
        send(GESTURE.side(), GESTURE.advance(QuickTransferInput::resolveAt, mouseX, mouseY));
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
                && QuickTransferRules.isSupportedScreenClass(screen.getClass().getName());
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
        if (supports(screen)) {
            Slot slot = findInventorySlot((AbstractContainerScreen<?>) screen, x, y);
            if (slot != null) {
                return new Hit(Side.INVENTORY, slot.getContainerSlot(), slot.hasItem());
            }
        }
        return null;
    }

    /**
     * Own hit test (not {@code hoveredSlot}, which is only refreshed at render time and would miss
     * slots a fast drag passes over between frames).
     */
    private static Slot findInventorySlot(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (!QuickTransferRules.isTransferableInventorySlot(
                    slot.container == minecraft.player.getInventory(), slot.getContainerSlot(), slot.isActive())) {
                continue;
            }
            int left = screen.getGuiLeft() + slot.x;
            int top = screen.getGuiTop() + slot.y;
            if (mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 16) {
                return slot;
            }
        }
        return null;
    }

    private static void send(Side side, List<Integer> indices) {
        if (indices.isEmpty() || side == null) {
            return;
        }
        Direction direction = side == Side.INVENTORY ? Direction.TO_BACKPACK : Direction.TO_INVENTORY;
        PacketDistributor.sendToServer(
                BackpackQuickTransferPayload.of(direction, indices.stream().mapToInt(Integer::intValue).toArray()));
    }
}
