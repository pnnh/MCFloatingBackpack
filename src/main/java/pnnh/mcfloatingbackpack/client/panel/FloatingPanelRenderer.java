package pnnh.mcfloatingbackpack.client.panel;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * Draws an empty floating panel in the classic vanilla inventory gray style
 * (same palette feel as {@code textures/gui/container/inventory.png}).
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingPanelRenderer {
    /** Same ballpark as vanilla tooltips so we sit above InventoryScreen's entity (z≈50). */
    private static final float OVERLAY_Z = 400.0F;

    /** Classic container panel fill (~inventory.png background). */
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_EDGE_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_EDGE_DARK = 0xFF555555;
    private static final int COLOR_OUTLINE = 0xFF000000;
    /** Vanilla container title color. */
    private static final int COLOR_TITLE_TEXT = 0xFF404040;
    /** Empty slot well (inset like inventory slots). */
    private static final int COLOR_SLOT = 0xFF8B8B8B;
    private static final int COLOR_SLOT_LIGHT = 0xFF373737;
    private static final int COLOR_SLOT_DARK = 0xFFFFFFFF;

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_COLS = 9;
    private static final int SLOT_ROWS = 3;
    private static final int SLOT_PAD_X = 7;
    private static final int SLOT_PAD_TOP = 17;

    private FloatingPanelRenderer() {
    }

    public static void render(GuiGraphics graphics, FloatingPanelState state) {
        if (!state.isVisible()) {
            return;
        }
        int x = state.getPosX();
        int y = state.getPosY();
        int w = state.getWidth();
        int h = state.getHeight();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        RenderSystem.disableDepthTest();

        drawVanillaPanel(graphics, x, y, w, h);
        drawEmptySlots(graphics, x, y);

        Minecraft minecraft = Minecraft.getInstance();
        Component title = Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".title");
        graphics.drawString(minecraft.font, title, x + 8, y + 6, COLOR_TITLE_TEXT, false);

        graphics.flush();
        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }

    /** Beveled gray box matching vanilla container chrome. */
    private static void drawVanillaPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_OUTLINE);
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        // Top / left highlight
        graphics.fill(x, y, x + w, y + 1, COLOR_EDGE_LIGHT);
        graphics.fill(x, y, x + 1, y + h, COLOR_EDGE_LIGHT);
        // Bottom / right shade
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_EDGE_DARK);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_EDGE_DARK);
    }

    /** Decorative empty slots only — no item interaction in M1. */
    private static void drawEmptySlots(GuiGraphics graphics, int panelX, int panelY) {
        for (int row = 0; row < SLOT_ROWS; row++) {
            for (int col = 0; col < SLOT_COLS; col++) {
                int sx = panelX + SLOT_PAD_X + col * SLOT_SIZE;
                int sy = panelY + SLOT_PAD_TOP + row * SLOT_SIZE;
                drawSlot(graphics, sx, sy);
            }
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        int x2 = x + SLOT_SIZE;
        int y2 = y + SLOT_SIZE;
        graphics.fill(x, y, x2, y2, COLOR_SLOT);
        // Inset bevel (dark top-left, light bottom-right) like vanilla slots
        graphics.fill(x, y, x2, y + 1, COLOR_SLOT_LIGHT);
        graphics.fill(x, y, x + 1, y2, COLOR_SLOT_LIGHT);
        graphics.fill(x, y2 - 1, x2, y2, COLOR_SLOT_DARK);
        graphics.fill(x2 - 1, y, x2, y2, COLOR_SLOT_DARK);
    }
}
