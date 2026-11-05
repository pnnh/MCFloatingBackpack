package pnnh.mcfloatingbackpack.client.panel;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Preview;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * Floating panel chrome in vanilla inventory gray, with synced item stacks.
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingPanelRenderer {
    /**
     * Base depth of the whole panel. A vanilla tooltip (and JEI's, which uses the same call) draws its
     * background at z=400 and its text with a drop shadow, whose main layer sits 0.03 nearer (Font
     * SHADOW_OFFSET). With the panel at exactly 400 the panel covers the tooltip background (equal depth
     * passes) but not its text (nearer), which is the "background under the panel, text over it" tear.
     * Anything the panel must paint over has to be strictly below this; the tooltip's own item images
     * reach roughly 400 + 150 + a few.
     */
    private static final float OVERLAY_Z = 800.0F;

    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_EDGE_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_EDGE_DARK = 0xFF555555;
    private static final int COLOR_OUTLINE = 0xFF000000;
    private static final int COLOR_SLOT = 0xFF8B8B8B;
    /** Tool-area wells (016): three different light colours, all distinct from the main area's gray. */
    private static final int COLOR_SLOT_LEFT = 0xFFB5D3EE;
    private static final int COLOR_SLOT_RIGHT = 0xFFBFE5B5;
    private static final int COLOR_SLOT_BOTTOM = 0xFFF0DDA0;
    /** Scroll arrow (016, shrunk in 017): dark body 3 deep, light outline 4 deep (at most 7 wide); its tip touches the area's edge. Tune here after looking at it in game. */
    private static final int ARROW_SIZE = 3;
    private static final int COLOR_ARROW = 0xFF3A3A3A;
    private static final int COLOR_ARROW_OUTLINE = 0xFFF2F2F2;
    private static final int COLOR_SLOT_LIGHT = 0xFF373737;
    private static final int COLOR_SLOT_DARK = 0xFFFFFFFF;
    /** Same white translucent hover used by vanilla container slots. */
    private static final int COLOR_SLOT_HOVER = 0x80FFFFFF;
    /** Same translucent white vanilla paints over slots taking part in a drag. */
    private static final int COLOR_DRAG_SLOT = 0x80FFFFFF;
    /** Border of a selected slot (014): golden, so it differs from the white hover and drag-preview fills. */
    private static final int COLOR_SELECTED = 0xFFFFD34D;
    private static final int COLOR_CLOSE_BUTTON = 0xFFB33A3A;
    private static final int COLOR_CLOSE_GLYPH = 0xFFFFFFFF;
    private static final int COLOR_RESIZE_GRIP = 0xFF555555;

    private FloatingPanelRenderer() {
    }

    public static void render(GuiGraphics graphics, FloatingPanelState state, int mouseX, int mouseY) {
        if (!state.isVisible()) {
            return;
        }
        // Text queued earlier this frame (a tooltip's text stays in the shared buffer until something
        // flushes it) is drawn now, before the panel, so the panel is painted after it. Whether the panel
        // then covers it is decided by depth: see OVERLAY_Z.
        graphics.flush();
        Minecraft minecraft = Minecraft.getInstance();
        state.setTotalRows(ClientBackpackView.get().rows());
        state.layout(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());

        int x = state.getPosX();
        int y = state.getPosY();
        int w = state.getWidth();
        int h = state.getHeight();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        RenderSystem.disableDepthTest();

        drawVanillaPanel(graphics, x, y, w, h);
        drawSlotsWithItems(graphics, mouseX, mouseY);
        drawScrollArrows(graphics, ClientBackpackView.get().layout());
        // 018: the expanded tag panel replaces the main area (its cells, selection and arrows are not drawn).
        if (TagPanelState.get().isOpen()) {
            TagPanelRenderer.render(graphics, ClientBackpackView.get().layout().rect(PanelArea.MAIN), mouseX, mouseY);
        }
        drawCloseButton(graphics, state);
        drawResizeHandle(graphics, state);
        drawTitleIcon(graphics, state, minecraft, mouseX, mouseY);

        ItemStack cursor = cursorStack(minecraft);
        if (!cursor.isEmpty()) {
            // 013: while a drag-distribute is previewed the cursor shows what would be left over.
            Preview<ItemStack> preview = PanelDragInput.preview();
            ItemStack shown = preview != null ? preview.cursorLeft() : cursor;
            String text = preview != null && shown.isEmpty() ? ChatFormatting.YELLOW + "0" : null;
            ItemStack drawn = shown.isEmpty() ? cursor : shown;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 32.0F);
            graphics.renderItem(drawn, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(minecraft.font, drawn, mouseX - 8, mouseY - 8, text);
            graphics.pose().popPose();
        }

        graphics.flush();
        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }

    /** Falls back to the generic panel title if the slot count doesn't match a known tier. */
    private static Component titleFor(int slotCount) {
        BackpackTier tier = BackpackTier.byCapacity(slotCount);
        if (tier == null) {
            return Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".title");
        }
        return Component.translatable("item." + MCFloatingBackpack.MOD_ID + "." + tier.id());
    }

    /**
     * Fixed-size backpack-tier icon in place of the (possibly overflowing) title text; hovering
     * it shows the name as a tooltip instead. Geometry must match
     * {@link FloatingPanelState#hitsTitleIcon}.
     */
    private static void drawTitleIcon(
            GuiGraphics graphics, FloatingPanelState state, Minecraft minecraft, int mouseX, int mouseY) {
        BackpackTier tier = BackpackTier.byCapacity(ClientBackpackView.get().slotCount());
        if (tier == null) {
            return;
        }
        ItemStack icon = ModItems.stackFor(tier);
        int iconX = state.getPosX() + 4;
        int iconY = state.getPosY() + 1;
        graphics.renderItem(icon, iconX, iconY);
        if (state.hitsTitleIcon(mouseX, mouseY)) {
            graphics.renderTooltip(minecraft.font, titleFor(ClientBackpackView.get().slotCount()), mouseX, mouseY);
        }
    }

    static ItemStack cursorStack(Minecraft minecraft) {
        if (minecraft.player != null
                && ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)
                && minecraft.player.containerMenu != null) {
            return minecraft.player.containerMenu.getCarried();
        }
        return ClientBackpackView.get().getCursor();
    }

    private static void drawVanillaPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_OUTLINE);
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOR_EDGE_LIGHT);
        graphics.fill(x, y, x + 1, y + h, COLOR_EDGE_LIGHT);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_EDGE_DARK);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_EDGE_DARK);
    }

    private static void drawSlotsWithItems(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientBackpackView view = ClientBackpackView.get();
        PanelLayout layout = view.layout();
        PanelLayout.Hit hovered = layout.hit(mouseX, mouseY);
        Preview<ItemStack> preview = PanelDragInput.preview();
        PanelSelection selection = PanelSelection.get();
        for (PanelArea area : PanelArea.values()) {
            if (area == PanelArea.MAIN && TagPanelState.get().isOpen()) {
                continue;
            }
            for (int ordinal = 0; ordinal < layout.visibleCount(area); ordinal++) {
                // A filler cell (beyond the area's capacity, or the empty tail of the main area's last row)
                // is left as plain panel background: the panel stays one rectangle without inventing slots.
                int index = layout.slotIndex(area, ordinal);
                if (index < 0) {
                    continue;
                }
                int sx = layout.cellX(area, ordinal);
                int sy = layout.cellY(area, ordinal);
                drawSlotWell(graphics, sx, sy, wellColor(area));
                ItemStack stack = area == PanelArea.MAIN ? view.getStackInSlot(index) : view.getToolStack(area, index);
                // 013 drag preview and 014 selection only exist in the main area.
                int previewIndex = area == PanelArea.MAIN && preview != null ? preview.slots().indexOf(index) : -1;
                if (previewIndex >= 0) {
                    // 013: the slot is part of the drag; show what it would hold after the release.
                    graphics.fill(sx, sy, sx + PanelSlots.SLOT_SIZE, sy + PanelSlots.SLOT_SIZE, COLOR_DRAG_SLOT);
                    ItemStack planned = preview.stacks().get(previewIndex);
                    if (!planned.isEmpty()) {
                        String text = preview.capped().get(previewIndex)
                                ? ChatFormatting.YELLOW + String.valueOf(planned.getCount())
                                : null;
                        graphics.renderItem(planned, sx + 1, sy + 1);
                        graphics.renderItemDecorations(minecraft.font, planned, sx + 1, sy + 1, text);
                    }
                } else if (!stack.isEmpty()) {
                    graphics.renderItem(stack, sx + 1, sy + 1);
                    graphics.renderItemDecorations(minecraft.font, stack, sx + 1, sy + 1);
                }
                if (area == PanelArea.MAIN && selection.contains(index)) {
                    drawSelectionBorder(graphics, sx, sy);
                }
                if (hovered != null && hovered.area() == area && hovered.index() == index) {
                    graphics.fill(sx, sy, sx + PanelSlots.SLOT_SIZE, sy + PanelSlots.SLOT_SIZE, COLOR_SLOT_HOVER);
                }
            }
        }
    }

    private static int wellColor(PanelArea area) {
        return switch (area) {
            case MAIN -> COLOR_SLOT;
            case LEFT -> COLOR_SLOT_LEFT;
            case RIGHT -> COLOR_SLOT_RIGHT;
            case BOTTOM -> COLOR_SLOT_BOTTOM;
        };
    }

    /** One pixel frame just inside the slot. */
    private static void drawSelectionBorder(GuiGraphics graphics, int x, int y) {
        int x2 = x + PanelSlots.SLOT_SIZE;
        int y2 = y + PanelSlots.SLOT_SIZE;
        graphics.fill(x, y, x2, y + 1, COLOR_SELECTED);
        graphics.fill(x, y2 - 1, x2, y2, COLOR_SELECTED);
        graphics.fill(x, y + 1, x + 1, y2 - 1, COLOR_SELECTED);
        graphics.fill(x2 - 1, y + 1, x2, y2 - 1, COLOR_SELECTED);
    }

    private static void drawSlotWell(GuiGraphics graphics, int x, int y, int color) {
        int x2 = x + PanelSlots.SLOT_SIZE;
        int y2 = y + PanelSlots.SLOT_SIZE;
        graphics.fill(x, y, x2, y2, color);
        graphics.fill(x, y, x2, y + 1, COLOR_SLOT_LIGHT);
        graphics.fill(x, y, x + 1, y2, COLOR_SLOT_LIGHT);
        graphics.fill(x, y2 - 1, x2, y2, COLOR_SLOT_DARK);
        graphics.fill(x2 - 1, y, x2, y2, COLOR_SLOT_DARK);
    }

    /**
     * Small flat arrows at the edges of an area that has hidden content (016): up/down for the main area and the
     * side columns, left/right for the bottom row. Both ends show one when there is content on both sides; none
     * when the whole area is visible. They are drawn over the cells' edge and never take part in hit-testing.
     */
    private static void drawScrollArrows(GuiGraphics graphics, PanelLayout layout) {
        for (PanelArea area : PanelArea.values()) {
            if (area == PanelArea.MAIN && TagPanelState.get().isOpen()) {
                continue;
            }
            PanelLayout.Rect r = layout.rect(area);
            boolean back = layout.canBack(area);
            boolean forward = layout.canForward(area);
            if (area == PanelArea.BOTTOM) {
                int cy = r.y() + r.h() / 2;
                if (back) {
                    drawArrow(graphics, r.x(), cy, -1, 0);
                }
                if (forward) {
                    drawArrow(graphics, r.x() + r.w() - 1, cy, 1, 0);
                }
            } else {
                int cx = r.x() + r.w() / 2;
                if (back) {
                    drawArrow(graphics, cx, r.y(), 0, -1);
                }
                if (forward) {
                    drawArrow(graphics, cx, r.y() + r.h() - 1, 0, 1);
                }
            }
        }
    }

    /**
     * A triangle with its tip at (tipX, tipY) pointing along (dx, dy): a light outline triangle with a dark one
     * inside it, built from 1-pixel lines of width 1, 3, 5 (outline 1, 3, 5, 7) so it needs no texture.
     */
    private static void drawArrow(GuiGraphics graphics, int tipX, int tipY, int dx, int dy) {
        drawTriangle(graphics, tipX, tipY, dx, dy, ARROW_SIZE + 1, COLOR_ARROW_OUTLINE);
        drawTriangle(graphics, tipX - dx, tipY - dy, dx, dy, ARROW_SIZE, COLOR_ARROW);
    }

    private static void drawTriangle(GuiGraphics graphics, int tipX, int tipY, int dx, int dy, int size, int color) {
        for (int i = 0; i < size; i++) {
            // Line i lies i steps back from the tip, 2*i+1 pixels wide, centred on the axis.
            int bx = tipX - dx * i;
            int by = tipY - dy * i;
            if (dy != 0) {
                graphics.fill(bx - i, by, bx + i + 1, by + 1, color);
            } else {
                graphics.fill(bx, by - i, bx + 1, by + i + 1, color);
            }
        }
    }

    /** Geometry must match {@link FloatingPanelState#hitsCloseButton}. */
    private static void drawCloseButton(GuiGraphics graphics, FloatingPanelState state) {
        Minecraft minecraft = Minecraft.getInstance();
        int size = FloatingPanelState.CLOSE_BUTTON_SIZE;
        int x2 = state.getPosX() + state.getWidth() - 3;
        int x1 = x2 - size;
        int y1 = state.getPosY() + 2;
        int y2 = y1 + size;
        graphics.fill(x1, y1, x2, y2, COLOR_CLOSE_BUTTON);
        Component glyph = Component.literal("x");
        int textX = x1 + (size - minecraft.font.width(glyph)) / 2;
        int textY = y1 + (size - minecraft.font.lineHeight) / 2;
        graphics.drawString(minecraft.font, glyph, textX, textY, COLOR_CLOSE_GLYPH, false);
    }

    /** Geometry anchored at the same corner as {@link FloatingPanelState#hitsResizeHandle}. */
    private static void drawResizeHandle(GuiGraphics graphics, FloatingPanelState state) {
        int x2 = state.getPosX() + state.getWidth();
        int y2 = state.getPosY() + state.getHeight();
        for (int i = 0; i < 3; i++) {
            int offset = (i + 1) * 3;
            int px = x2 - offset;
            int py = y2 - offset;
            graphics.fill(px, py, px + 2, py + 2, COLOR_RESIZE_GRIP);
        }
    }
}
