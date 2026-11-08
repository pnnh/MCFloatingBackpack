package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewModel.Row;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Rect;

/**
 * 025: draws the tag overview (the list page and the detail page) over the main area, in the same look as the tag panel
 * (018). Flat fills, text and item icons only, so it adds no textures. Colors and sizes are the constants below.
 */
@OnlyIn(Dist.CLIENT)
public final class TagOverviewRenderer {
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
    private static final int COLOR_ROW = 0xFFE6E6E6;
    private static final int COLOR_ROW_ALT = 0xFFD4D4D4;
    private static final int COLOR_ROW_HOVER = 0xFFBFD8F5;
    private static final int COLOR_BUTTON = 0xFFC9C9C9;
    private static final int COLOR_BUTTON_HOVER = 0xFFE8E8E8;
    private static final int COLOR_BUTTON_ACTIVE = 0xFFBFD8F5;
    private static final int COLOR_CONFIRM = 0xFFB33A3A;
    private static final int COLOR_MISSING = 0xFF9A9A9A;

    private TagOverviewRenderer() {
    }

    /**
     * The menu icon (three bars) at the status row's right end; shared with the selection view's tag panel. {@code open} is
     * true while the overview is up, when the icon means "back".
     */
    public static void drawMenuIcon(GuiGraphics graphics, PanelLayout.Rect area, int mouseX, int mouseY, boolean open) {
        Rect icon = TagOverviewLayout.menuIconRect(area.w());
        int x = area.x() + icon.x();
        int y = area.y() + icon.y();
        boolean hovered = icon.contains(mouseX - area.x(), mouseY - area.y());
        box(graphics, x, y, icon.w(), icon.h(), hovered ? COLOR_BUTTON_HOVER : open ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON);
        for (int bar = 0; bar < 3; bar++) {
            graphics.fill(x + 3, y + 3 + bar * 3, x + icon.w() - 3, y + 5 + bar * 3, COLOR_TEXT);
        }
    }

    /** The tooltip of the menu icon while the mouse is over it. */
    public static void drawMenuTooltip(
            GuiGraphics graphics, PanelLayout.Rect area, int mouseX, int mouseY, boolean open) {
        Rect icon = TagOverviewLayout.menuIconRect(area.w());
        if (icon.contains(mouseX - area.x(), mouseY - area.y())) {
            graphics.renderComponentTooltip(
                    Minecraft.getInstance().font,
                    List.of(Component.translatable(open ? "overview.mcfloatingbackpack.icon.close" : "overview.mcfloatingbackpack.icon.open")),
                    mouseX,
                    mouseY);
        }
    }

    /** @param area the main area's rectangle: the overview covers exactly it. */
    public static void render(GuiGraphics graphics, PanelLayout.Rect area, int mouseX, int mouseY) {
        TagOverviewContext.syncDetail();
        int x2 = area.x() + area.w();
        int y2 = area.y() + area.h();
        graphics.fill(area.x(), area.y(), x2, y2, COLOR_BACKGROUND);
        graphics.fill(area.x(), area.y(), x2, area.y() + 1, COLOR_EDGE_DARK);
        graphics.fill(area.x(), area.y(), area.x() + 1, y2, COLOR_EDGE_DARK);
        graphics.fill(area.x(), y2 - 1, x2, y2, COLOR_EDGE_LIGHT);
        graphics.fill(x2 - 1, area.y(), x2, y2, COLOR_EDGE_LIGHT);
        if (TagOverviewState.get().view() == TagOverviewState.View.DETAIL) {
            renderDetail(graphics, area, mouseX, mouseY);
        } else {
            renderList(graphics, area, mouseX, mouseY);
        }
        drawMenuIcon(graphics, area, mouseX, mouseY, true);
        drawMenuTooltip(graphics, area, mouseX, mouseY, true);
    }

    // ---------------------------------------------------------------- list

    private static void renderList(GuiGraphics graphics, PanelLayout.Rect area, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        TagOverviewState state = TagOverviewState.get();
        List<Row<ResourceLocation>> rows = TagOverviewContext.rows();
        TagOverviewLayout layout = TagOverviewContext.layout(area.w(), area.h(), rows.size());
        int ox = area.x();
        int oy = area.y();
        long now = System.currentTimeMillis();

        // status row: the title (cut off before the menu icon)
        Component title = Component.translatable("overview.mcfloatingbackpack.title", rows.size());
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), area.w() - 8 - 18), ox + 4, oy + 5, COLOR_TEXT, false);
        graphics.fill(ox + 1, oy + TagPanelLayout.ROW - 1, ox + area.w() - 1, oy + TagPanelLayout.ROW, COLOR_TAG_BORDER);

        drawRows(graphics, font, area, rows, layout, mouseX, mouseY, now);
        drawFilterRow(graphics, font, area, layout, mouseX, mouseY);
        drawDropdown(graphics, font, area, layout, mouseX, mouseY);

        // tooltips: the hovered row lists the item's tags; the clear button and the filter mark say what they do
        TagOverviewLayout.Hit hit = layout.hit(mouseX - ox, mouseY - oy);
        if (hit.kind() == TagOverviewLayout.Kind.ROW && hit.index() < rows.size()) {
            Row<ResourceLocation> row = rows.get(hit.index());
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(row.name()));
            if (!TagOverviewContext.registered(row.key())) {
                lines.add(Component.literal(row.key().toString()).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            for (String tag : row.tags()) {
                lines.add(Component.literal("- " + TagPanelContext.label(tag)));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (hit.kind() == TagOverviewLayout.Kind.CLEAR) {
            graphics.renderComponentTooltip(
                    font, List.of(Component.translatable("overview.mcfloatingbackpack.clear.tooltip")), mouseX, mouseY);
        } else if (hit.kind() == TagOverviewLayout.Kind.FILTER_CLEAR) {
            graphics.renderComponentTooltip(
                    font, List.of(Component.translatable("overview.mcfloatingbackpack.filter.clear")), mouseX, mouseY);
        }
    }

    private static void drawRows(
            GuiGraphics graphics,
            Font font,
            PanelLayout.Rect area,
            List<Row<ResourceLocation>> rows,
            TagOverviewLayout layout,
            int mouseX,
            int mouseY,
            long now) {
        int ox = area.x();
        int oy = area.y();
        if (rows.isEmpty()) {
            int top = layout.visibleRows() > 0 ? layout.rowRect(0).y() : TagPanelLayout.ROW + 20;
            String key = TagOverviewState.get().filter() != null
                    ? "overview.mcfloatingbackpack.filter.none"
                    : "overview.mcfloatingbackpack.empty";
            graphics.drawString(font, Component.translatable(key), ox + TagOverviewLayout.PAD + 4, oy + top + 4, COLOR_TEXT_DIM, false);
            return;
        }
        int listTop = layout.rowRect(0).y();
        graphics.enableScissor(ox, oy + listTop, ox + area.w(), oy + area.h() - 1);
        TagOverviewLayout.Hit hit = layout.hit(mouseX - ox, mouseY - oy);
        for (int i = 0; i < layout.visibleCount(); i++) {
            int index = layout.firstVisible() + i;
            Row<ResourceLocation> row = rows.get(index);
            Rect r = layout.rowRect(i);
            boolean hovered = (hit.kind() == TagOverviewLayout.Kind.ROW || hit.kind() == TagOverviewLayout.Kind.CLEAR)
                    && hit.index() == index;
            graphics.fill(ox + r.x(), oy + r.y(), ox + r.x() + r.w(), oy + r.y() + r.h(),
                    hovered ? COLOR_ROW_HOVER : (index % 2 == 0 ? COLOR_ROW : COLOR_ROW_ALT));
            drawItemIcon(graphics, row.key(), ox + layout.iconRect(i).x(), oy + layout.iconRect(i).y());
            Rect name = layout.nameRect(i);
            graphics.drawString(font, font.plainSubstrByWidth(row.name(), name.w()), ox + name.x(), oy + name.y() + 5,
                    TagOverviewContext.registered(row.key()) ? COLOR_TEXT : COLOR_MISSING, false);
            Rect count = layout.countRect(i);
            String number = String.valueOf(row.tags().size());
            graphics.drawString(font, number, ox + count.x() + count.w() - font.width(number), oy + count.y() + 5, COLOR_TEXT_DIM, false);
            drawClearButton(graphics, font, ox, oy, layout.clearRect(i), row.key(), now, hit.kind() == TagOverviewLayout.Kind.CLEAR && hit.index() == index);
        }
        graphics.disableScissor();
    }

    private static void drawClearButton(
            GuiGraphics graphics, Font font, int ox, int oy, Rect r, ResourceLocation key, long now, boolean hovered) {
        boolean armed = TagOverviewState.get().confirm().isArmed(key.toString(), now);
        int x = ox + r.x();
        int y = oy + r.y();
        box(graphics, x, y, r.w(), r.h(), armed ? COLOR_CONFIRM : hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        Component label = Component.translatable(armed ? "overview.mcfloatingbackpack.clear.confirm" : "overview.mcfloatingbackpack.clear");
        graphics.drawString(font, label, x + (r.w() - font.width(label)) / 2, y + (r.h() - 8) / 2,
                armed ? COLOR_DELETE_GLYPH : COLOR_TEXT, false);
    }

    private static void drawFilterRow(
            GuiGraphics graphics, Font font, PanelLayout.Rect area, TagOverviewLayout layout, int mouseX, int mouseY) {
        TagOverviewState state = TagOverviewState.get();
        int ox = area.x();
        int oy = area.y();
        Rect filter = layout.filterRect();
        box(graphics, ox + filter.x(), oy + filter.y(), filter.w(), filter.h(), COLOR_TEXTBOX);
        int tx = ox + filter.x() + 3;
        int ty = oy + filter.y() + 3;
        if (state.isFilterEditing()) {
            String typed = state.filterInput().text();
            if (typed.isEmpty()) {
                graphics.drawString(font, Component.translatable("overview.mcfloatingbackpack.filter.placeholder"), tx, ty, COLOR_TEXT_DIM, false);
            } else {
                graphics.drawString(font, typed, tx, ty, COLOR_TEXT, false);
            }
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + font.width(typed.substring(0, state.filterInput().caret()));
                graphics.fill(cx, ty - 1, cx + 1, ty + 9, COLOR_TEXT);
            }
        } else if (state.filter() != null) {
            graphics.drawString(font, TagPanelContext.label(state.filter()), tx, ty, COLOR_TEXT, false);
            Rect clear = layout.filterClearRect();
            if (clear != null) {
                boolean hovered = clear.contains(mouseX - ox, mouseY - oy);
                box(graphics, ox + clear.x(), oy + clear.y(), clear.w(), clear.h(), hovered ? COLOR_DELETE : COLOR_BUTTON);
                graphics.drawString(font, "x", ox + clear.x() + (clear.w() - font.width("x")) / 2, oy + clear.y() + 2,
                        hovered ? COLOR_DELETE_GLYPH : COLOR_TEXT, false);
            }
        } else {
            graphics.drawString(font, Component.translatable("overview.mcfloatingbackpack.filter.placeholder"), tx, ty, COLOR_TEXT_DIM, false);
        }
    }

    private static void drawDropdown(
            GuiGraphics graphics, Font font, PanelLayout.Rect area, TagOverviewLayout layout, int mouseX, int mouseY) {
        if (layout.dropdownCount() == 0) {
            return;
        }
        TagOverviewState state = TagOverviewState.get();
        List<String> offered = TagOverviewContext.filterDropdown();
        for (int i = 0; i < layout.dropdownCount() && i < offered.size(); i++) {
            Rect row = layout.dropdownRect(i);
            boolean hovered = row.contains(mouseX - area.x(), mouseY - area.y());
            box(graphics, area.x() + row.x(), area.y() + row.y(), row.w(), row.h(),
                    i == state.filterDropdownIndex() || hovered ? COLOR_DROPDOWN_SELECTED : COLOR_DROPDOWN);
            drawDropdownText(graphics, font, area.x() + row.x(), area.y() + row.y(), offered.get(i), false);
        }
    }

    private static void drawDropdownText(GuiGraphics graphics, Font font, int x, int y, String id, boolean blocked) {
        String name = TagPanelContext.label(id);
        graphics.drawString(font, name, x + 3, y + 3, blocked ? COLOR_TEXT_DIM : COLOR_TEXT, false);
        if (!name.equals(id)) {
            // The stored tag is the id; show it when the displayed name is something else (other languages).
            graphics.drawString(font, id, x + 3 + font.width(name) + 4, y + 3, COLOR_TEXT_DIM, false);
        }
    }

    // ---------------------------------------------------------------- detail

    private static void renderDetail(GuiGraphics graphics, PanelLayout.Rect area, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        ResourceLocation key = TagOverviewContext.detailKey();
        if (key == null) {
            return;
        }
        TagPanelModel<ResourceLocation> model = TagOverviewContext.detailModel();
        TagPanelLayout layout = TagOverviewContext.detailLayout(area.w(), area.h(), model);
        int ox = area.x();
        int oy = area.y();
        int x2 = ox + area.w();
        int y2 = oy + area.h();

        graphics.enableScissor(ox, oy + TagPanelLayout.ROW, x2, y2);
        drawDetailContent(graphics, font, area, model, layout, mouseX, mouseY);
        graphics.disableScissor();

        // status row: back button, the item's icon and name (cut off before the menu icon)
        graphics.fill(ox + 1, oy + 1, x2 - 1, oy + TagPanelLayout.ROW, COLOR_BACKGROUND);
        Rect back = TagOverviewContext.BACK_RECT;
        boolean backHovered = back.contains(mouseX - ox, mouseY - oy);
        box(graphics, ox + back.x(), oy + back.y(), back.w(), back.h(), backHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.drawString(font, "<", ox + back.x() + (back.w() - font.width("<")) / 2, oy + back.y() + 3, COLOR_TEXT, false);
        drawItemIcon(graphics, key, ox + back.x() + back.w() + 3, oy + 1);
        int nameX = ox + back.x() + back.w() + 3 + 16 + 3;
        int nameW = Math.max(0, ox + area.w() - 18 - nameX);
        graphics.drawString(font, font.plainSubstrByWidth(TagOverviewContext.nameOf(key), nameW), nameX, oy + 5,
                TagOverviewContext.registered(key) ? COLOR_TEXT : COLOR_MISSING, false);
        graphics.fill(ox + 1, oy + TagPanelLayout.ROW - 1, x2 - 1, oy + TagPanelLayout.ROW, COLOR_TAG_BORDER);

        if (backHovered) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable("overview.mcfloatingbackpack.detail.back")), mouseX, mouseY);
        }
        drawDetailTooltips(graphics, font, area, key, model, layout, mouseX, mouseY);
    }

    private static void drawDetailContent(
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
            box(graphics, ox + r.x(), oy + r.y(), r.w(), r.h(), COLOR_TAG);
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
            box(graphics, ox + textBox.x(), oy + textBox.y(), textBox.w(), textBox.h(), COLOR_TEXTBOX);
            TagPanelState state = TagPanelState.get();
            String typed = state.input().text();
            int tx = ox + textBox.x() + 3;
            int ty = oy + textBox.y() + 3;
            if (typed.isEmpty()) {
                graphics.drawString(font, Component.translatable("tag.mcfloatingbackpack.add.placeholder"), tx, ty, COLOR_TEXT_DIM, false);
            } else {
                graphics.drawString(font, typed, tx, ty, COLOR_TEXT, false);
            }
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + font.width(typed.substring(0, state.input().caret()));
                graphics.fill(cx, ty - 1, cx + 1, ty + 9, COLOR_TEXT);
            }
            List<String> offered = TagOverviewContext.detailDropdown();
            for (int i = 0; i < layout.dropdownCount() && i < offered.size(); i++) {
                Rect row = layout.dropdownRect(i);
                boolean hovered = row.contains(mouseX - ox, mouseY - oy);
                box(graphics, ox + row.x(), oy + row.y(), row.w(), row.h(),
                        i == state.dropdownIndex() || hovered ? COLOR_DROPDOWN_SELECTED : COLOR_DROPDOWN);
                // 022: the trash preset is greyed out while the item is protected.
                drawDropdownText(graphics, font, ox + row.x(), oy + row.y(), offered.get(i), model.blocks(offered.get(i)));
            }
        }
        Rect plus = layout.plusRect();
        if (plus != null) {
            box(graphics, ox + plus.x(), oy + plus.y(), plus.w(), plus.h(), model.canAdd() ? COLOR_PLUS : COLOR_PLUS_DISABLED);
            graphics.drawString(font, "+", ox + plus.x() + (plus.w() - font.width("+")) / 2, oy + plus.y() + 3,
                    model.canAdd() ? COLOR_TEXT : COLOR_TEXT_DIM, false);
        }
    }

    private static void drawDetailTooltips(
            GuiGraphics graphics,
            Font font,
            PanelLayout.Rect area,
            ResourceLocation key,
            TagPanelModel<ResourceLocation> model,
            TagPanelLayout layout,
            int mouseX,
            int mouseY) {
        TagPanelLayout.Hit hit = layout.hit(mouseX - area.x(), mouseY - area.y());
        if (hit.kind() == TagPanelLayout.Kind.PLUS && !model.canAdd() && layout.plusRect() != null) {
            List<Component> lines = new ArrayList<>();
            if (!TagOverviewContext.registered(key)) {
                lines.add(Component.translatable("overview.mcfloatingbackpack.detail.missing"));
            } else {
                lines.add(Component.translatable("tag.mcfloatingbackpack.full"));
                lines.add(Component.literal(TagOverviewContext.nameOf(key)));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (hit.kind() == TagPanelLayout.Kind.DROPDOWN) {
            List<String> offered = TagOverviewContext.detailDropdown();
            if (hit.index() < offered.size() && model.blocks(offered.get(hit.index()))) {
                graphics.renderComponentTooltip(font, List.of(TagPanelContext.protectedMessage(model)), mouseX, mouseY);
            }
        }
    }

    // ---------------------------------------------------------------- shared

    /** The item's icon, or for an item no longer in the game a grey square with a question mark. */
    private static void drawItemIcon(GuiGraphics graphics, ResourceLocation key, int x, int y) {
        ItemStack stack = TagOverviewContext.stackFor(key);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            return;
        }
        graphics.fill(x, y, x + 16, y + 16, COLOR_MISSING);
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, "?", x + (16 - font.width("?")) / 2, y + 4, COLOR_DELETE_GLYPH, false);
    }

    private static void box(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + h, COLOR_TAG_BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }
}
