package pnnh.mcfloatingbackpack.client.panel;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * Floating panel chrome in vanilla inventory gray, with synced item stacks.
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingPanelRenderer {
    private static final float OVERLAY_Z = 400.0F;

    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_EDGE_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_EDGE_DARK = 0xFF555555;
    private static final int COLOR_OUTLINE = 0xFF000000;
    private static final int COLOR_TITLE_TEXT = 0xFF404040;
    private static final int COLOR_SLOT = 0xFF8B8B8B;
    private static final int COLOR_SLOT_LIGHT = 0xFF373737;
    private static final int COLOR_SLOT_DARK = 0xFFFFFFFF;

    private FloatingPanelRenderer() {
    }

    public static void render(GuiGraphics graphics, FloatingPanelState state, int mouseX, int mouseY) {
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
        drawSlotsWithItems(graphics, state);

        Minecraft minecraft = Minecraft.getInstance();
        Component title = Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".title");
        graphics.drawString(minecraft.font, title, x + 8, y + 6, COLOR_TITLE_TEXT, false);

        ItemStack cursor = ClientBackpackView.get().getCursor();
        if (!cursor.isEmpty()) {
            graphics.renderItem(cursor, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(minecraft.font, cursor, mouseX - 8, mouseY - 8);
        }

        graphics.flush();
        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }

    private static void drawVanillaPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_OUTLINE);
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOR_EDGE_LIGHT);
        graphics.fill(x, y, x + 1, y + h, COLOR_EDGE_LIGHT);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_EDGE_DARK);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_EDGE_DARK);
    }

    private static void drawSlotsWithItems(GuiGraphics graphics, FloatingPanelState state) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientBackpackView view = ClientBackpackView.get();
        for (int i = 0; i < PanelSlots.SLOT_COUNT; i++) {
            int sx = PanelSlots.slotX(state, i);
            int sy = PanelSlots.slotY(state, i);
            drawSlotWell(graphics, sx, sy);
            ItemStack stack = view.getStackInSlot(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, sx + 1, sy + 1);
                graphics.renderItemDecorations(minecraft.font, stack, sx + 1, sy + 1);
            }
        }
    }

    private static void drawSlotWell(GuiGraphics graphics, int x, int y) {
        int x2 = x + PanelSlots.SLOT_SIZE;
        int y2 = y + PanelSlots.SLOT_SIZE;
        graphics.fill(x, y, x2, y2, COLOR_SLOT);
        graphics.fill(x, y, x2, y + 1, COLOR_SLOT_LIGHT);
        graphics.fill(x, y, x + 1, y2, COLOR_SLOT_LIGHT);
        graphics.fill(x, y2 - 1, x2, y2, COLOR_SLOT_DARK);
        graphics.fill(x2 - 1, y, x2, y2, COLOR_SLOT_DARK);
    }
}
