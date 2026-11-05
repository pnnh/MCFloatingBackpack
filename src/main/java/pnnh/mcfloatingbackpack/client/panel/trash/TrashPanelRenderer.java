package pnnh.mcfloatingbackpack.client.panel.trash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;
import pnnh.mcfloatingbackpack.client.panel.PanelSlots;
import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * 021: draws the trash can panel: the frame and a title bar in the floating panel's own colors, and nine cells in the
 * right area's well color (the trash can lives there). Everything is {@code fill} and text, no textures. Called from
 * {@link FloatingPanelRenderer#render} inside its overlay depth, after the panel's own content.
 */
@OnlyIn(Dist.CLIENT)
public final class TrashPanelRenderer {
    /** Vanilla's container title color. */
    private static final int COLOR_TITLE = 0xFF404040;

    private TrashPanelRenderer() {
    }

    private static final int COLOR_ICON_BG = 0xFFA8A8A8;
    private static final int COLOR_ICON_BG_HOVER = 0xFFC9D6F0;
    private static final int COLOR_ICON_BG_OFF = 0xFF9A9A9A;
    private static final int COLOR_ICON_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_ICON_DARK = 0xFF555555;
    private static final int COLOR_DUMP_GLYPH = 0xFF3A3A3A;
    private static final int COLOR_DELETE_GLYPH = 0xFFB33A3A;
    private static final int COLOR_GLYPH_OFF = 0xFF707070;
    /** The widest a tooltip line gets before it wraps. */
    private static final int TOOLTIP_WIDTH = 170;

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        TrashPanelLayout layout = TrashPanelInput.layout();
        Rect frame = layout.rect();
        FloatingPanelRenderer.drawVanillaPanel(graphics, frame.x(), frame.y(), frame.w(), frame.h());

        Rect title = layout.titleRect();
        Component text = Component.translatable("trash.mcfloatingbackpack.title");
        // The icons take the right end of the bar; a title that does not fit before them is cut.
        graphics.drawString(
                minecraft.font,
                minecraft.font.plainSubstrByWidth(text.getString(), layout.titleTextWidth()),
                title.x() + TrashPanelLayout.PAD + 1,
                title.y() + (title.h() - minecraft.font.lineHeight) / 2 + 1,
                COLOR_TITLE,
                false);
        drawIcons(graphics, minecraft, layout, mouseX, mouseY);

        int hovered = layout.cellAt(mouseX, mouseY);
        for (int i = 0; i < TrashPanelLayout.CELLS; i++) {
            Rect cell = layout.cellRect(i);
            FloatingPanelRenderer.drawSlotWell(graphics, cell.x(), cell.y(), FloatingPanelRenderer.wellColor(PanelArea.RIGHT));
            ItemStack stack = ClientBackpackView.get().trash().get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, cell.x() + 1, cell.y() + 1);
                graphics.renderItemDecorations(minecraft.font, stack, cell.x() + 1, cell.y() + 1);
            }
            if (i == hovered) {
                graphics.fill(
                        cell.x(),
                        cell.y(),
                        cell.x() + PanelSlots.SLOT_SIZE,
                        cell.y() + PanelSlots.SLOT_SIZE,
                        FloatingPanelRenderer.COLOR_SLOT_HOVER);
            }
        }
    }

    /** The dump and delete icons (022), drawn with fills only: a raised square with a glyph, grey when it cannot be used. */
    private static void drawIcons(GuiGraphics graphics, Minecraft minecraft, TrashPanelLayout layout, int mouseX, int mouseY) {
        TrashPanelLayout.Icon hovered = layout.iconAt(mouseX, mouseY);
        boolean deleteOn = TrashActions.deleteEnabled();
        for (TrashPanelLayout.Icon icon : TrashPanelLayout.Icon.values()) {
            Rect r = layout.iconRect(icon);
            boolean enabled = icon == TrashPanelLayout.Icon.DUMP || deleteOn;
            int bg = !enabled ? COLOR_ICON_BG_OFF : icon == hovered ? COLOR_ICON_BG_HOVER : COLOR_ICON_BG;
            graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), bg);
            graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, COLOR_ICON_LIGHT);
            graphics.fill(r.x(), r.y(), r.x() + 1, r.y() + r.h(), COLOR_ICON_LIGHT);
            graphics.fill(r.x(), r.y() + r.h() - 1, r.x() + r.w(), r.y() + r.h(), COLOR_ICON_DARK);
            graphics.fill(r.x() + r.w() - 1, r.y(), r.x() + r.w(), r.y() + r.h(), COLOR_ICON_DARK);
            if (icon == TrashPanelLayout.Icon.DUMP) {
                drawDumpGlyph(graphics, r, enabled ? COLOR_DUMP_GLYPH : COLOR_GLYPH_OFF);
            } else {
                drawDeleteGlyph(graphics, r, enabled ? COLOR_DELETE_GLYPH : COLOR_GLYPH_OFF);
            }
        }
        if (hovered != null) {
            String key = hovered == TrashPanelLayout.Icon.DUMP
                    ? "trash.mcfloatingbackpack.dump.tooltip"
                    : deleteOn
                            ? "trash.mcfloatingbackpack.delete.tooltip.enabled"
                            : "trash.mcfloatingbackpack.delete.tooltip.disabled";
            graphics.renderTooltip(minecraft.font, minecraft.font.split(Component.translatable(key), TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }

    /** A down arrow over a tray. */
    private static void drawDumpGlyph(GuiGraphics graphics, Rect r, int color) {
        int ox = r.x();
        int oy = r.y();
        graphics.fill(ox + 4, oy + 1, ox + 5, oy + 4, color);
        graphics.fill(ox + 2, oy + 4, ox + 7, oy + 5, color);
        graphics.fill(ox + 3, oy + 5, ox + 6, oy + 6, color);
        graphics.fill(ox + 4, oy + 6, ox + 5, oy + 7, color);
        graphics.fill(ox + 1, oy + 7, ox + 8, oy + 8, color);
    }

    /** A cross. */
    private static void drawDeleteGlyph(GuiGraphics graphics, Rect r, int color) {
        for (int i = 0; i < 5; i++) {
            graphics.fill(r.x() + 2 + i, r.y() + 2 + i, r.x() + 3 + i, r.y() + 3 + i, color);
            graphics.fill(r.x() + 6 - i, r.y() + 2 + i, r.x() + 7 - i, r.y() + 3 + i, color);
        }
    }
}
