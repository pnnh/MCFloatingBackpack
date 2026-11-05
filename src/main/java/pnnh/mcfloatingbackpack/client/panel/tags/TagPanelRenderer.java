package pnnh.mcfloatingbackpack.client.panel.tags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Rect;

/**
 * 018: draws the tag panel over the main area. Flat fills and text only, so it adds no textures. Colors and sizes are
 * the constants below.
 */
@OnlyIn(Dist.CLIENT)
public final class TagPanelRenderer {
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

    private TagPanelRenderer() {
    }

    /** @param area the main area's rectangle: the tag panel covers exactly it. */
    public static void render(GuiGraphics graphics, PanelLayout.Rect area, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        TagPanelModel<ResourceLocation> model = TagPanelContext.model();
        TagPanelLayout layout = TagPanelContext.layout(area.w(), area.h(), model);

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
        drawBlockedTooltip(graphics, font, area, model, layout, mouseX, mouseY);
        // A narrow panel cuts the status text off: hovering the row shows all of it.
        if (statusTruncated && layout.hit(mouseX - area.x(), mouseY - area.y()).kind() == TagPanelLayout.Kind.STATUS) {
            graphics.renderComponentTooltip(font, java.util.List.of(statusText), mouseX, mouseY);
        }
    }

    private static void drawStatus(
            GuiGraphics graphics, Font font, PanelLayout.Rect area, TagPanelModel<ResourceLocation> model, TagPanelLayout layout) {
        Rect status = layout.statusRect();
        graphics.fill(area.x() + 1, area.y() + 1, area.x() + area.w() - 1, area.y() + status.h(), COLOR_BACKGROUND);
        Component text = model.status() == TagPanelModel.Status.NO_ITEMS
                ? Component.translatable("tag.mcfloatingbackpack.hint.select")
                : Component.translatable("tag.mcfloatingbackpack.status", model.categories().size());
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
            TagPanelModel<ResourceLocation> model,
            TagPanelLayout layout,
            int mouseX,
            int mouseY) {
        int ox = area.x();
        int oy = area.y();
        for (int i = 0; i < layout.tagCount(); i++) {
            Rect r = layout.tagRect(i);
            box(graphics, ox, oy, r, COLOR_TAG);
            graphics.drawString(font, TagPanelContext.label(model.commonTags().get(i)),
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
            graphics.drawString(font, Component.translatable("tag.mcfloatingbackpack.none"),
                    ox + none.x() + TagPanelLayout.BLOCK_PAD, oy + none.y() + 3, COLOR_TEXT_DIM, false);
        }
        Rect textBox = layout.textBoxRect();
        if (textBox != null) {
            box(graphics, ox, oy, textBox, COLOR_TEXTBOX);
            TagPanelState state = TagPanelState.get();
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
            java.util.List<String> offered = TagPanelContext.dropdown();
            for (int i = 0; i < layout.dropdownCount(); i++) {
                Rect row = layout.dropdownRect(i);
                boolean hovered = row.contains(mouseX - ox, mouseY - oy);
                box(graphics, ox, oy, row, i == state.dropdownIndex() || hovered ? COLOR_DROPDOWN_SELECTED : COLOR_DROPDOWN);
                String id = offered.get(i);
                String name = TagPanelContext.label(id);
                // 022: the trash preset is greyed out while a protected item is selected.
                boolean blocked = model.blocks(id);
                graphics.drawString(font, name, ox + row.x() + 3, oy + row.y() + 3, blocked ? COLOR_TEXT_DIM : COLOR_TEXT, false);
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

    /** 022: over the greyed-out trash entry of the drop-down: which selected items are protected. */
    private static void drawBlockedTooltip(
            GuiGraphics graphics,
            Font font,
            PanelLayout.Rect area,
            TagPanelModel<ResourceLocation> model,
            TagPanelLayout layout,
            int mouseX,
            int mouseY) {
        TagPanelLayout.Hit hit = layout.hit(mouseX - area.x(), mouseY - area.y());
        if (hit.kind() != TagPanelLayout.Kind.DROPDOWN) {
            return;
        }
        java.util.List<String> offered = TagPanelContext.dropdown();
        if (hit.index() >= offered.size() || !model.blocks(offered.get(hit.index()))) {
            return;
        }
        graphics.renderComponentTooltip(font, java.util.List.of(TagPanelContext.protectedMessage(model)), mouseX, mouseY);
    }

    /** Over a greyed-out plus: why it is grey, and which items are the reason. */
    private static void drawPlusTooltip(
            GuiGraphics graphics,
            Font font,
            PanelLayout.Rect area,
            TagPanelModel<ResourceLocation> model,
            TagPanelLayout layout,
            int mouseX,
            int mouseY) {
        if (model.canAdd() || layout.plusRect() == null) {
            return;
        }
        TagPanelLayout.Hit hit = layout.hit(mouseX - area.x(), mouseY - area.y());
        if (hit.kind() != TagPanelLayout.Kind.PLUS) {
            return;
        }
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (model.status() == TagPanelModel.Status.NO_ITEMS) {
            lines.add(Component.translatable("tag.mcfloatingbackpack.hint.select_tooltip"));
        } else {
            lines.add(Component.translatable("tag.mcfloatingbackpack.full"));
            for (ResourceLocation id : model.fullItems()) {
                lines.add(TagPanelContext.itemName(id));
            }
            lines.add(Component.translatable("tag.mcfloatingbackpack.full.batch"));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
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
