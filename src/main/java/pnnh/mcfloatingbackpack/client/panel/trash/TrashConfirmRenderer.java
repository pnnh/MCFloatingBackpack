package pnnh.mcfloatingbackpack.client.panel.trash;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;

/**
 * 022: the confirmation dialog: a translucent mask over the whole floating panel and, in its middle, the question and
 * two buttons. Fills and text only. Called from {@link FloatingPanelRenderer#render} inside its overlay depth.
 */
@OnlyIn(Dist.CLIENT)
public final class TrashConfirmRenderer {
    private static final int COLOR_MASK = 0xA0000000;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_BUTTON = 0xFFC6C6C6;
    private static final int COLOR_BUTTON_HOVER = 0xFFD9E4F7;
    private static final int COLOR_BUTTON_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_BUTTON_DARK = 0xFF555555;
    private static final int COLOR_BUTTON_EDGE = 0xFF000000;

    private TrashConfirmRenderer() {
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<FormattedCharSequence> lines = TrashConfirmInput.lines(minecraft);
        TrashConfirmLayout layout = TrashConfirmInput.layout(minecraft);

        Rect mask = layout.mask();
        graphics.fill(mask.x(), mask.y(), mask.x() + mask.w(), mask.y() + mask.h(), COLOR_MASK);

        Rect dialog = layout.dialog();
        FloatingPanelRenderer.drawVanillaPanel(graphics, dialog.x(), dialog.y(), dialog.w(), dialog.h());
        int textX = dialog.x() + TrashConfirmLayout.PAD / 2 + 1;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), textX, dialog.y() + TrashConfirmLayout.PAD + i * font.lineHeight, COLOR_TEXT, false);
        }

        TrashConfirmLayout.Hit hovered = layout.hit(mouseX, mouseY);
        drawButton(graphics, font, layout.confirmButton(), Component.translatable("trash.mcfloatingbackpack.confirm.yes"),
                hovered == TrashConfirmLayout.Hit.CONFIRM);
        drawButton(graphics, font, layout.cancelButton(), Component.translatable("trash.mcfloatingbackpack.confirm.no"),
                hovered == TrashConfirmLayout.Hit.CANCEL);
    }

    private static void drawButton(GuiGraphics graphics, Font font, Rect r, Component label, boolean hovered) {
        int x2 = r.x() + r.w();
        int y2 = r.y() + r.h();
        graphics.fill(r.x() - 1, r.y() - 1, x2 + 1, y2 + 1, COLOR_BUTTON_EDGE);
        graphics.fill(r.x(), r.y(), x2, y2, hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.fill(r.x(), r.y(), x2, r.y() + 1, COLOR_BUTTON_LIGHT);
        graphics.fill(r.x(), r.y(), r.x() + 1, y2, COLOR_BUTTON_LIGHT);
        graphics.fill(r.x(), y2 - 1, x2, y2, COLOR_BUTTON_DARK);
        graphics.fill(x2 - 1, r.y(), x2, y2, COLOR_BUTTON_DARK);
        String text = font.plainSubstrByWidth(label.getString(), r.w() - 4);
        graphics.drawString(font, text, r.x() + (r.w() - font.width(text)) / 2, r.y() + (r.h() - font.lineHeight) / 2 + 1, COLOR_TEXT, false);
    }
}
