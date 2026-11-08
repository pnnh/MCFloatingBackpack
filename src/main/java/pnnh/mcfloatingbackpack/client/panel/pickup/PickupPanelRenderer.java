package pnnh.mcfloatingbackpack.client.panel.pickup;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelContext;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Rect;

/**
 * 024: draws the pickup blacklist page over the main area, in the same look as the tag panel (018). Flat fills and text
 * only, so it adds no textures.
 */
@OnlyIn(Dist.CLIENT)
public final class PickupPanelRenderer {
    private static final int COLOR_BACKGROUND = 0xFFDCDCDC;
    private static final int COLOR_EDGE_DARK = 0xFF555555;
    private static final int COLOR_EDGE_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF303030;
    private static final int COLOR_TEXT_DIM = 0xFF707070;
    private static final int COLOR_TAG = 0xFFF4F4F4;
    private static final int COLOR_TAG_BORDER = 0xFF8B8B8B;
    private static final int COLOR_PLUS = 0xFFB9E3B0;
    private static final int COLOR_PLUS_DISABLED = 0xFFB0B0B0;
    private static final int COLOR_DELETE = 0xFFB33A3A;
    private static final int COLOR_DELETE_GLYPH = 0xFFFFFFFF;
    private static final int COLOR_TEXTBOX = 0xFFFFFFFF;
    private static final int COLOR_DROPDOWN = 0xFFEFEFEF;
    private static final int COLOR_DROPDOWN_SELECTED = 0xFFBFD8F5;

    /** The status row's text and whether it was cut off, so a tooltip can show it whole. */
    private static boolean statusTruncated;
    private static Component statusText = Component.empty();

    private PickupPanelRenderer() {
    }

    /** @param area the main area's rectangle: the page covers exactly it. */
    public static void render(GuiGraphics graphics, PanelLayout.Rect area, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        PickupPanelModel model = PickupPanelContext.model();
        TagPanelLayout layout = PickupPanelContext.layout(area.w(), area.h(), model);

        int x2 = area.x() + area.w();
        int y2 = area.y() + area.h();
        graphics.fill(area.x(), area.y(), x2, y2, COLOR_BACKGROUND);
        graphics.fill(area.x(), area.y(), x2, area.y() + 1, COLOR_EDGE_DARK);
        graphics.fill(area.x(), area.y(), area.x() + 1, y2, COLOR_EDGE_DARK);
        graphics.fill(area.x(), y2 - 1, x2, y2, COLOR_EDGE_LIGHT);
        graphics.fill(x2 - 1, area.y(), x2, y2, COLOR_EDGE_LIGHT);

        // Content first, clipped to the area under the status row; the status row is drawn on top of it.
        graphics.enableScissor(area.x(), area.y() + TagPanelLayout.ROW, x2, y2);
        drawContent(graphics, font, area, model, layout, mouseX, mouseY);
        graphics.disableScissor();
        drawStatus(graphics, font, area, model, layout);
        drawPlusTooltip(graphics, font, area, model, layout, mouseX, mouseY);
        // A narrow panel cuts the status text off: hovering the row shows all of it.
        if (statusTruncated && layout.hit(mouseX - area.x(), mouseY - area.y()).kind() == TagPanelLayout.Kind.STATUS) {
            graphics.renderComponentTooltip(font, List.of(statusText), mouseX, mouseY);
        }
    }

    private static void drawStatus(
            GuiGraphics graphics, Font font, PanelLayout.Rect area, PickupPanelModel model, TagPanelLayout layout) {
        Rect status = layout.statusRect();
        graphics.fill(area.x() + 1, area.y() + 1, area.x() + area.w() - 1, area.y() + status.h(), COLOR_BACKGROUND);
        Component text = Component.translatable("pickup.mcfloatingbackpack.status", model.count(), model.max());
        String fitted = font.plainSubstrByWidth(text.getString(), area.w() - 8);
        graphics.drawString(font, fitted, area.x() + 4, area.y() + 5, COLOR_TEXT, false);
        statusTruncated = !fitted.equals(text.getString());
        statusText = text;
        graphics.fill(area.x() + 1, area.y() + status.h() - 1, area.x() + area.w() - 1, area.y() + status.h(), COLOR_TAG_BORDER);
    }

    private static void drawContent(
            GuiGraphics graphics,
            Font font,
            PanelLayout.Rect area,
            PickupPanelModel model,
            TagPanelLayout layout,
            int mouseX,
            int mouseY) {
        int ox = area.x();
        int oy = area.y();
        for (int i = 0; i < layout.tagCount(); i++) {
            Rect r = layout.tagRect(i);
            box(graphics, ox, oy, r, COLOR_TAG);
            graphics.drawString(font, TagPanelContext.label(model.tags().get(i)),
                    ox + r.x() + TagPanelLayout.BLOCK_PAD, oy + r.y() + 3, COLOR_TEXT, false);
        }
        Rect mark = layout.deleteRect();
        if (mark != null) {
            graphics.fill(ox + mark.x(), oy + mark.y(), ox + mark.x() + mark.w(), oy + mark.y() + mark.h(), COLOR_DELETE);
            graphics.drawString(font, "x", ox + mark.x() + (mark.w() - font.width("x")) / 2, oy + mark.y() + 3,
                    COLOR_DELETE_GLYPH, false);
        }
        Rect none = layout.noneRect();
        if (none != null) {
            graphics.drawString(font, Component.translatable("pickup.mcfloatingbackpack.none"),
                    ox + none.x() + TagPanelLayout.BLOCK_PAD, oy + none.y() + 3, COLOR_TEXT_DIM, false);
        }
        Rect textBox = layout.textBoxRect();
        if (textBox != null) {
            box(graphics, ox, oy, textBox, COLOR_TEXTBOX);
            PickupPanelState state = PickupPanelState.get();
            String typed = state.input().text();
            int tx = ox + textBox.x() + 3;
            int ty = oy + textBox.y() + 3;
            if (typed.isEmpty()) {
                graphics.drawString(font, Component.translatable("tag.mcfloatingbackpack.add.placeholder"), tx, ty,
                        COLOR_TEXT_DIM, false);
            } else {
                graphics.drawString(font, typed, tx, ty, COLOR_TEXT, false);
            }
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + font.width(typed.substring(0, state.input().caret()));
                graphics.fill(cx, ty - 1, cx + 1, ty + 9, COLOR_TEXT);
            }
            List<String> offered = PickupPanelContext.dropdown();
            for (int i = 0; i < layout.dropdownCount(); i++) {
                Rect row = layout.dropdownRect(i);
                boolean hovered = row.contains(mouseX - ox, mouseY - oy);
                box(graphics, ox, oy, row, i == state.dropdownIndex() || hovered ? COLOR_DROPDOWN_SELECTED : COLOR_DROPDOWN);
                String id = offered.get(i);
                String name = TagPanelContext.label(id);
                graphics.drawString(font, name, ox + row.x() + 3, oy + row.y() + 3, COLOR_TEXT, false);
                if (!name.equals(id)) {
                    // The stored tag is the id; show it when the displayed name is something else (other languages).
                    int idX = ox + row.x() + 3 + font.width(name) + 4;
                    graphics.drawString(font, id, idX, oy + row.y() + 3, COLOR_TEXT_DIM, false);
                }
            }
        }
        Rect plus = layout.plusRect();
        if (plus != null) {
            box(graphics, ox, oy, plus, model.canAdd() ? COLOR_PLUS : COLOR_PLUS_DISABLED);
            graphics.drawString(font, "+", ox + plus.x() + (plus.w() - font.width("+")) / 2, oy + plus.y() + 3,
                    model.canAdd() ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        }
    }

    /** Over a greyed-out plus: the list is full. */
    private static void drawPlusTooltip(
            GuiGraphics graphics,
            Font font,
            PanelLayout.Rect area,
            PickupPanelModel model,
            TagPanelLayout layout,
            int mouseX,
            int mouseY) {
        if (model.canAdd() || !model.isFull() || layout.plusRect() == null) {
            return;
        }
        TagPanelLayout.Hit hit = layout.hit(mouseX - area.x(), mouseY - area.y());
        if (hit.kind() != TagPanelLayout.Kind.PLUS) {
            return;
        }
        graphics.renderComponentTooltip(
                font, List.of(Component.translatable("pickup.mcfloatingbackpack.full", model.max())), mouseX, mouseY);
    }

    private static void box(GuiGraphics graphics, int ox, int oy, Rect r, int color) {
        int x1 = ox + r.x();
        int y1 = oy + r.y();
        int x2 = x1 + r.w();
        int y2 = y1 + r.h();
        graphics.fill(x1, y1, x2, y2, COLOR_TAG_BORDER);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
    }
}
