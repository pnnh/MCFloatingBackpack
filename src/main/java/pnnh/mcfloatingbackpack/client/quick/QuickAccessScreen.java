package pnnh.mcfloatingbackpack.client.quick;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.FloatingBackpackKeys;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.quick.QuickLists;
import pnnh.mcfloatingbackpack.inventory.quick.QuickViewport;
import pnnh.mcfloatingbackpack.network.QuickSwapPayload;

/**
 * 067: the quick-access grid. Its own screen, not the panel host: no pause, no dim, no window. Items are read from the
 * backpack the B key would open. Holding the key keeps the grid up; releasing it closes the grid. A click waits for
 * {@code quick_swap_result} before the grid closes or accepts another.
 */
@OnlyIn(Dist.CLIENT)
public final class QuickAccessScreen extends Screen {
    private static final int ARROW_SIZE = 3;
    private static final int COLOR_ARROW = 0xFF3A3A3A;
    private static final int COLOR_ARROW_OUTLINE = 0xFFF2F2F2;
    /** Same translucent white the panel paints over a hovered slot. */
    private static final int COLOR_HOVER = 0x80FFFFFF;

    private int scroll;
    private boolean awaiting;
    private QuickViewport.Fit fit = QuickViewport.layout(1, 1, 0, 0);
    private List<Integer> candidates = List.of();
    private BackpackInventory inventory;

    public QuickAccessScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void init() {
        super.init();
        refresh();
        layout();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        refresh();
        layout();
        if (inventory == null || fit.columns() <= 0) {
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int row = 0; row < fit.rows(); row++) {
            for (int column = 0; column < fit.columns(); column++) {
                int index = (fit.scroll() + row) * fit.columns() + column;
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }
                ItemStack stack = inventory.getStackInSlot(candidates.get(index));
                int x = fit.originX() + column * QuickViewport.SLOT;
                int y = fit.originY() + row * QuickViewport.SLOT;
                boolean underMouse = mouseX >= x && mouseY >= y && mouseX < x + QuickViewport.SLOT && mouseY < y + QuickViewport.SLOT;
                if (underMouse) {
                    graphics.fill(x, y, x + QuickViewport.SLOT, y + QuickViewport.SLOT, COLOR_HOVER);
                    hovered = stack;
                }
                if (!stack.isEmpty()) {
                    int pad = (QuickViewport.SLOT - QuickViewport.ITEM) / 2;
                    float scale = QuickViewport.ITEM / 16.0F;
                    graphics.pose().pushPose();
                    graphics.pose().translate(x + pad, y + pad, 0);
                    graphics.pose().scale(scale, scale, 1.0F);
                    graphics.renderItem(stack, 0, 0);
                    graphics.renderItemDecorations(font, stack, 0, 0);
                    graphics.pose().popPose();
                }
            }
        }
        int centerX = fit.originX() + fit.columns() * QuickViewport.SLOT / 2;
        if (fit.moreAbove()) {
            drawArrow(graphics, centerX, fit.originY(), 0, -1);
        }
        if (fit.moreBelow()) {
            drawArrow(graphics, centerX, fit.originY() + fit.rows() * QuickViewport.SLOT - 1, 0, 1);
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The grid stays up for as long as the key is held, so a miss or a right click must not close it.
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        refresh();
        layout();
        int index = QuickViewport.hit(fit, mouseX, mouseY);
        if (index < 0 || inventory == null || minecraft.player == null) {
            return true;
        }
        if (awaiting) {
            return true;
        }
        BackpackRef ref = BackpackRef.resolve(minecraft.player);
        if (ref == null || index >= candidates.size()) {
            dismiss();
            return true;
        }
        awaiting = true;
        PacketDistributor.sendToServer(new QuickSwapPayload(ref.source().ordinal(), ref.invIndex(), candidates.get(index)));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            scroll--;
        } else if (scrollY < 0) {
            scroll++;
        }
        layout();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dismiss();
            return true;
        }
        // Repeats of the held key used to close the grid and, with the next press, open it again.
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isQuickKey(keyCode, scanCode)) {
            dismiss();
        }
        return true;
    }

    /** The server's answer. {@code ok} closes the grid; a refusal leaves it open for another click. */
    public void onResult(boolean ok) {
        if (ok) {
            dismiss();
            return;
        }
        awaiting = false;
    }

    private boolean isQuickKey(int keyCode, int scanCode) {
        return FloatingBackpackKeys.QUICK_ACCESS.getKey().equals(InputConstants.getKey(keyCode, scanCode));
    }

    private void refresh() {
        inventory = null;
        candidates = List.of();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        BackpackRef ref = BackpackRef.resolve(minecraft.player);
        if (ref == null) {
            return;
        }
        inventory = BackpackInventory.fromItem(ref.getStack(minecraft.player));
        candidates = QuickLists.candidates(inventory);
    }

    private void layout() {
        fit = QuickViewport.layout(width, height, candidates.size(), scroll);
        scroll = fit.scroll();
    }

    public void dismiss() {
        if (minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    /** Copied from the panel's scroll arrow: the tip sits on the edge and points outward. */
    private static void drawArrow(GuiGraphics graphics, int tipX, int tipY, int dx, int dy) {
        drawTriangle(graphics, tipX, tipY, dx, dy, ARROW_SIZE + 1, COLOR_ARROW_OUTLINE);
        drawTriangle(graphics, tipX - dx, tipY - dy, dx, dy, ARROW_SIZE, COLOR_ARROW);
    }

    private static void drawTriangle(GuiGraphics graphics, int tipX, int tipY, int dx, int dy, int size, int color) {
        for (int i = 0; i < size; i++) {
            int bx = tipX - dx * i;
            int by = tipY - dy * i;
            if (dy != 0) {
                graphics.fill(bx - i, by, bx + i + 1, by + 1, color);
            } else {
                graphics.fill(bx, by - i, bx + 1, by + i + 1, color);
            }
        }
    }
}
