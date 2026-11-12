package pnnh.mcfloatingbackpack.client.panel.search;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.PanelSlots;
import pnnh.mcfloatingbackpack.client.panel.SearchBoxLayout;
import pnnh.mcfloatingbackpack.client.panel.TitleBarLayout;

/**
 * 042: draws the search's part of the title bar - the magnifier, or the open box with its text, caret, hit count and clear button -
 * and the frame that marks a cell the query found. Everything is drawn with {@code fill} and text (no texture), like the close and
 * snapshot buttons. The geometry comes from {@link TitleBarLayout} and {@link SearchBoxLayout}, the hit tests from
 * {@link FloatingPanelState}: they must agree.
 */
@OnlyIn(Dist.CLIENT)
public final class SearchRenderer {
    /** The frame of a found cell and of the search box while a query is on: a deep blue, unlike the yellow of a selected cell. */
    public static final int COLOR_HIT = 0xFF1E3FD0;

    private static final int COLOR_BUTTON = 0xFF3F8F6A;
    private static final int COLOR_GLYPH = 0xFFFFFFFF;
    private static final int COLOR_BOX = 0xFFFFFFFF;
    private static final int COLOR_BOX_EDGE = 0xFF555555;
    private static final int COLOR_TEXT = 0xFF303030;
    private static final int COLOR_DIM = 0xFF707070;
    private static final int COLOR_CLEAR = 0xFF6E6E6E;
    private static final int CARET_BLINK_MS = 500;

    private SearchRenderer() {
    }

    /** The magnifier when the box is closed; the box when it is open. Tooltips are drawn here too. */
    public static void render(GuiGraphics graphics, FloatingPanelState panel, Minecraft minecraft, int mouseX, int mouseY) {
        SearchState state = SearchState.get();
        if (state.isOpen()) {
            drawBox(graphics, panel, minecraft, state, mouseX, mouseY);
        } else {
            drawMagnifier(graphics, panel, minecraft, mouseX, mouseY);
        }
    }

    /** A one pixel frame just inside a cell, in the colour of a found cell. */
    public static void drawHitFrame(GuiGraphics graphics, int x, int y) {
        int x2 = x + PanelSlots.SLOT_SIZE;
        int y2 = y + PanelSlots.SLOT_SIZE;
        graphics.fill(x, y, x2, y + 1, COLOR_HIT);
        graphics.fill(x, y2 - 1, x2, y2, COLOR_HIT);
        graphics.fill(x, y + 1, x + 1, y2 - 1, COLOR_HIT);
        graphics.fill(x2 - 1, y + 1, x2, y2 - 1, COLOR_HIT);
    }

    private static void drawMagnifier(GuiGraphics graphics, FloatingPanelState panel, Minecraft minecraft, int mouseX, int mouseY) {
        int size = TitleBarLayout.BUTTON_SIZE;
        int x1 = panel.titleBar().searchLeft();
        int y1 = panel.getPosY() + 2;
        graphics.fill(x1, y1, x1 + size, y1 + size, COLOR_BUTTON);
        // the lens: a ring of 5 by 5 pixels, and the handle running down to the right
        graphics.fill(x1 + 3, y1 + 2, x1 + 6, y1 + 3, COLOR_GLYPH);
        graphics.fill(x1 + 3, y1 + 6, x1 + 6, y1 + 7, COLOR_GLYPH);
        graphics.fill(x1 + 2, y1 + 3, x1 + 3, y1 + 6, COLOR_GLYPH);
        graphics.fill(x1 + 6, y1 + 3, x1 + 7, y1 + 6, COLOR_GLYPH);
        graphics.fill(x1 + 6, y1 + 6, x1 + 8, y1 + 8, COLOR_GLYPH);
        if (panel.hitsSearchButton(mouseX, mouseY)) {
            graphics.renderTooltip(
                    minecraft.font, Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".search.tooltip"), mouseX, mouseY);
        }
    }

    private static void drawBox(
            GuiGraphics graphics, FloatingPanelState panel, Minecraft minecraft, SearchState state, int mouseX, int mouseY) {
        Font font = minecraft.font;
        TitleBarLayout bar = panel.titleBar();
        int y1 = panel.getPosY() + 2;
        int y2 = y1 + TitleBarLayout.BUTTON_SIZE;
        int count = state.liveCount(SearchContext.source());
        String countText = String.valueOf(count);
        SearchBoxLayout inside = SearchBoxLayout.of(bar.boxLeft(), bar.boxRight(), state.isFiltering() ? font.width(countText) : 0);

        int edge = state.isFiltering() ? COLOR_HIT : COLOR_BOX_EDGE;
        graphics.fill(bar.boxLeft(), y1, bar.boxRight(), y2, edge);
        graphics.fill(bar.boxLeft() + 1, y1 + 1, bar.boxRight() - 1, y2 - 1, COLOR_BOX);

        int textY = y1 + (TitleBarLayout.BUTTON_SIZE - font.lineHeight) / 2 + 1;
        String text = state.input().text();
        int areaWidth = inside.textRight() - inside.textLeft();
        if (text.isEmpty()) {
            graphics.drawString(
                    font, font.plainSubstrByWidth(Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".search.placeholder").getString(), areaWidth),
                    inside.textLeft(), textY, COLOR_DIM, false);
        } else {
            int caret = state.input().caret();
            int start = SearchTextView.windowStart(text, caret, areaWidth, font::width);
            String shown = font.plainSubstrByWidth(text.substring(start), areaWidth);
            if (state.input().hasSelection()) {
                graphics.fill(inside.textLeft() - 1, y1 + 1, inside.textLeft() + font.width(shown) + 1, y2 - 1, 0xFFB5D3EE);
            }
            graphics.drawString(font, shown, inside.textLeft(), textY, COLOR_TEXT, false);
        }
        if (state.isFocused() && (System.currentTimeMillis() / CARET_BLINK_MS) % 2 == 0) {
            int caret = state.input().caret();
            int start = text.isEmpty() ? 0 : SearchTextView.windowStart(text, caret, areaWidth, font::width);
            int caretX = inside.textLeft() + font.width(text.substring(start, Math.max(start, caret)));
            graphics.fill(caretX, y1 + 2, caretX + 1, y2 - 2, COLOR_TEXT);
        }
        if (inside.hasCount()) {
            graphics.drawString(font, countText, inside.countLeft(), textY, COLOR_DIM, false);
        }
        // the clear button
        int clearLeft = inside.clearLeft();
        int clearTop = y1 + 1;
        graphics.fill(clearLeft, clearTop, clearLeft + SearchBoxLayout.CLEAR_SIZE, y2 - 1, COLOR_CLEAR);
        graphics.drawString(
                font, "x", clearLeft + (SearchBoxLayout.CLEAR_SIZE - font.width("x")) / 2, textY, COLOR_GLYPH, false);

        if (panel.hitsSearchClear(mouseX, mouseY)) {
            graphics.renderTooltip(
                    font, Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".search.clear"), mouseX, mouseY);
        } else if (state.isFiltering() && panel.hitsSearchBox(mouseX, mouseY)) {
            graphics.renderTooltip(
                    font, Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".search.count", count), mouseX, mouseY);
        }
    }
}
