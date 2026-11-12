package pnnh.mcfloatingbackpack.client.panel;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.PanelDragGesture.Preview;
import pnnh.mcfloatingbackpack.client.panel.pickup.PickupPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.search.SearchContext;
import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery;
import pnnh.mcfloatingbackpack.client.panel.search.SearchRenderer;
import pnnh.mcfloatingbackpack.client.panel.search.SearchState;
import pnnh.mcfloatingbackpack.client.panel.pickup.PickupPanelState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewRenderer;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmRenderer;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmState;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashPanelState;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.extension.CategoryNames;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;
import pnnh.mcfloatingbackpack.inventory.extension.HiddenHint;
import pnnh.mcfloatingbackpack.inventory.extension.RowLayout;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.ModItems;

import java.util.ArrayList;
import java.util.List;

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
    /** 022: the confirmation dialog sits above every slot item (icon 150, count text 200 over {@link #OVERLAY_Z}). */
    private static final float CONFIRM_Z = 300.0F;
    /** The item riding the cursor stays above the confirmation dialog. */
    private static final float CURSOR_Z = 400.0F;

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
    public static final int COLOR_SLOT_HOVER = 0x80FFFFFF;
    /** Same translucent white vanilla paints over slots taking part in a drag. */
    private static final int COLOR_DRAG_SLOT = 0x80FFFFFF;
    /** Border of a selected slot (014): golden, so it differs from the white hover and drag-preview fills. */
    private static final int COLOR_SELECTED = 0xFFFFD34D;
    /** 037: the hover over a cell of an extension row that cannot take what is on the cursor. */
    private static final int COLOR_SLOT_DENIED = 0x60FF3030;
    /** 037: how opaque the faint picture of an extension row's category is in its empty cells. */
    private static final float GHOST_ALPHA = 0.16F;
    private static final int COLOR_DENIED_TEXT = 0xFFFF6060;
    private static final int COLOR_HIDDEN_DOT = 0xFFFFD34D;
    private static final int COLOR_HIDDEN_DOT_OUTLINE = 0xFF3A2E00;
    private static final int COLOR_CLOSE_BUTTON = 0xFFB33A3A;
    private static final int COLOR_CLOSE_GLYPH = 0xFFFFFFFF;
    private static final int COLOR_SNAPSHOT_BUTTON = 0xFF3A6EA5;
    private static final int COLOR_RESIZE_GRIP = 0xFF555555;
    /** 026: the backpack's number in the title bar. */
    private static final int COLOR_TITLE_ID = 0xFF404040;

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
            // 025: the tag panel has a second view, the overview of every item the backpack has tags for.
            if (TagOverviewState.get().view() == TagOverviewState.View.SELECTION) {
                TagPanelRenderer.render(graphics, ClientBackpackView.get().layout().rect(PanelArea.MAIN), mouseX, mouseY);
            } else {
                TagOverviewRenderer.render(graphics, ClientBackpackView.get().layout().rect(PanelArea.MAIN), mouseX, mouseY);
            }
        }
        // 024: the pickup blacklist page covers the main area the same way (never together with the tag panel).
        if (PickupPanelState.get().isOpen()) {
            PickupPanelRenderer.render(graphics, ClientBackpackView.get().layout().rect(PanelArea.MAIN), mouseX, mouseY);
        }
        drawCloseButton(graphics, state);
        drawSnapshotButton(graphics, state, minecraft, mouseX, mouseY);
        // 044: /hidden: the main area has no cells, its summary of the hidden items is written there; the whole 037 hint is the tooltip
        PanelLayout.Rect mainArea = ClientBackpackView.get().layout().rect(PanelArea.MAIN);
        // (not over the tag panel or the blacklist page, which cover the main area themselves)
        if (SearchState.get().isOpen() && SearchState.get().command().type() == SearchQuery.Command.Type.HIDDEN
                && !TagPanelState.get().isOpen() && !PickupPanelState.get().isOpen()) {
            SearchRenderer.drawHiddenSummary(graphics, minecraft, mainArea);
            List<HiddenHint.Line> hiddenLines = HiddenHint.lines(ClientBackpackView.get().rowView().hidden());
            if (!hiddenLines.isEmpty() && mouseX >= mainArea.x() && mouseX < mainArea.x() + mainArea.w()
                    && mouseY >= mainArea.y() && mouseY < mainArea.y() + mainArea.h()) {
                drawHiddenHint(graphics, minecraft, hiddenLines, mouseX, mouseY);
            }
        }
        // 042: the magnifier, or the open search box in place of the number and the buttons
        SearchRenderer.render(graphics, state, minecraft, mouseX, mouseY);
        drawResizeHandle(graphics, state);
        drawTitleIcon(graphics, state, minecraft, mouseX, mouseY);
        drawRowTooltip(graphics, minecraft, mouseX, mouseY);
        // 021: the trash can panel hangs off the right edge, outside the rectangle everything above is drawn in.
        if (TrashPanelState.get().isOpen()) {
            TrashPanelRenderer.render(graphics, mouseX, mouseY);
        }
        // 022: a question waiting for the player's yes or no covers the whole panel.
        // Item icons are drawn 150 deep and their count text 200 deep (GuiGraphics), so a mask drawn at the panel's own
        // depth ends up *under* them; lift the whole dialog above that.
        if (TrashConfirmState.get().isPending()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, CONFIRM_Z);
            TrashConfirmRenderer.render(graphics, mouseX, mouseY);
            graphics.pose().popPose();
        }

        ItemStack cursor = cursorStack(minecraft);
        if (!cursor.isEmpty()) {
            // 013: while a drag-distribute is previewed the cursor shows what would be left over.
            Preview<ItemStack> preview = PanelDragInput.preview();
            ItemStack shown = preview != null ? preview.cursorLeft() : cursor;
            String text = preview != null && shown.isEmpty() ? ChatFormatting.YELLOW + "0" : null;
            ItemStack drawn = shown.isEmpty() ? cursor : shown;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, CURSOR_Z);
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
        // 036: by the tier's own capacity; the main area is bigger than that once extension rows are placed.
        BackpackTier tier = BackpackTier.byCapacity(ClientBackpackView.get().baseCapacity());
        if (tier == null) {
            return;
        }
        ItemStack icon = ModItems.stackFor(tier);
        int iconX = state.getPosX() + 4;
        int iconY = state.getPosY() + 1;
        graphics.renderItem(icon, iconX, iconY);
        // 037: some items are hidden (the rows they were in are not shown): a small yellow dot on the icon's corner and its hint.
        List<HiddenHint.Line> hidden = HiddenHint.lines(ClientBackpackView.get().rowView().hidden());
        boolean overDot = !hidden.isEmpty() && TitleDotLayout.hits(state.getPosX(), state.getPosY(), mouseX, mouseY);
        if (!hidden.isEmpty()) {
            drawHiddenDot(graphics, state);
        }
        if (overDot) {
            drawHiddenHint(graphics, minecraft, hidden, mouseX, mouseY);
        } else if (state.hitsTitleIcon(mouseX, mouseY)) {
            graphics.renderTooltip(minecraft.font, titleFor(ClientBackpackView.get().baseCapacity()), mouseX, mouseY);
        }
        // 042: the open search box takes the number's place
        if (!SearchState.get().isOpen()) {
            drawTitleId(graphics, state, minecraft, mouseX, mouseY);
        }
    }

    /** 037: the yellow dot: a 5 by 5 square without its corners, with a dark outline so it shows on the icon and on the title bar. */
    private static void drawHiddenDot(GuiGraphics graphics, FloatingPanelState state) {
        int x = TitleDotLayout.left(state.getPosX());
        int y = TitleDotLayout.top(state.getPosY());
        int size = TitleDotLayout.SIZE;
        // outline: the 5 by 5 square less its corners; fill: the 3 by 3 inside it
        graphics.fill(x + 1, y, x + size - 1, y + 1, COLOR_HIDDEN_DOT_OUTLINE);
        graphics.fill(x + 1, y + size - 1, x + size - 1, y + size, COLOR_HIDDEN_DOT_OUTLINE);
        graphics.fill(x, y + 1, x + 1, y + size - 1, COLOR_HIDDEN_DOT_OUTLINE);
        graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, COLOR_HIDDEN_DOT_OUTLINE);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, COLOR_HIDDEN_DOT);
    }

    /** 037: which categories have something hidden and in how many cells, and where to get it back. */
    private static void drawHiddenHint(GuiGraphics graphics, Minecraft minecraft, List<HiddenHint.Line> lines, int mouseX, int mouseY) {
        List<FormattedCharSequence> out = new ArrayList<>();
        out.addAll(minecraft.font.split(Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".hidden.title"), 220));
        for (HiddenHint.Line line : lines) {
            // A category this version does not know (made by a newer version) has no language entry: show its id.
            Component name = Component.translatableWithFallback(CategoryNames.langKey(line.category()), line.category());
            out.addAll(minecraft.font.split(
                    Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".hidden.line", name, line.cells()), 220));
        }
        out.addAll(minecraft.font.split(
                Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".hidden.hint").withStyle(ChatFormatting.GRAY), 220));
        graphics.renderTooltip(minecraft.font, out, mouseX, mouseY);
    }

    /**
     * 026: the backpack's number (from the identity the server put on the stack), right of the tier icon; cut off before the
     * close button when the panel is narrow, and shown whole in a tooltip.
     */
    private static void drawTitleId(
            GuiGraphics graphics, FloatingPanelState state, Minecraft minecraft, int mouseX, int mouseY) {
        if (minecraft.player == null) {
            return;
        }
        BackpackRef ref = ClientBackpackView.get().getActiveRef();
        if (ref == null) {
            return;
        }
        BackpackIdentity identity = ref.getStack(minecraft.player).get(ModDataComponents.BACKPACK_IDENTITY.get());
        if (identity == null) {
            return;
        }
        String id = identity.id();
        int textWidth = minecraft.font.width(id);
        TitleIdLayout layout = TitleIdLayout.of(state.getPosX(), state.titleBar().textRightLimit(), textWidth);
        if (layout.maxWidth() <= 0) {
            return;
        }
        String shown = layout.truncated() ? minecraft.font.plainSubstrByWidth(id, layout.maxWidth()) : id;
        graphics.drawString(minecraft.font, shown, layout.x(), state.getPosY() + 5, COLOR_TITLE_ID, false);
        if (state.hitsTitleId(mouseX, mouseY, textWidth)) {
            graphics.renderTooltip(
                    minecraft.font, Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".id.tooltip", id), mouseX, mouseY);
        }
    }

    public static ItemStack cursorStack(Minecraft minecraft) {
        if (minecraft.player != null
                && ContainerScreenCompat.isOpenContainerScreen(minecraft.screen)
                && minecraft.player.containerMenu != null) {
            return minecraft.player.containerMenu.getCarried();
        }
        return ClientBackpackView.get().getCursor();
    }

    public static void drawVanillaPanel(GuiGraphics graphics, int x, int y, int w, int h) {
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
        // 037: the rows of the category extensions, once per frame; without rows none of the code that uses it runs.
        RowLayout rows = rowLayout(view);
        ItemStack held = rows.hasRows() ? cursorStack(minecraft) : ItemStack.EMPTY;
        // 042: while a query is on the main area shows the found slots packed together, so the rows' lines and corners (which
        // say where a row begins and ends in the full grid) are left out; and the cells that match are framed
        SearchState search = SearchState.get();
        boolean searching = search.isFiltering();
        SearchQuery query = searching ? search.query() : SearchQuery.empty();
        for (PanelArea area : PanelArea.values()) {
            if (area == PanelArea.MAIN && (TagPanelState.get().isOpen() || PickupPanelState.get().isOpen())) {
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
                boolean ofARow = area == PanelArea.MAIN && rows.groupOf(index) >= 0;
                if (ofARow) {
                    drawRowDecor(graphics, rows, index, layout.cols(), sx, sy, searching);
                }
                // 013 drag preview and 014 selection only exist in the main area.
                int previewIndex = area == PanelArea.MAIN && preview != null ? preview.slots().indexOf(index) : -1;
                if (ofARow && stack.isEmpty() && previewIndex < 0) {
                    drawGhost(graphics, rows.categoryOf(index), sx, sy);
                }
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
                // 042: a cell the query found (a cell kept as an empty place is not found any more); the tool areas are framed, never filtered
                boolean found = searching
                        && !query.isEmpty()
                        && (area == PanelArea.MAIN ? !stack.isEmpty() : SearchContext.toolMatches(query, area, index));
                if (found) {
                    SearchRenderer.drawHitFrame(graphics, sx, sy);
                }
                if (area == PanelArea.MAIN && selection.contains(index)) {
                    // a selected cell that is also found: the selection goes one pixel in, the frame of the find stays outside
                    drawSelectionBorder(graphics, found ? sx + 1 : sx, found ? sy + 1 : sy, found ? PanelSlots.SLOT_SIZE - 2 : PanelSlots.SLOT_SIZE);
                }
                if (hovered != null && hovered.area() == area && hovered.index() == index) {
                    boolean denied = ofARow && !held.isEmpty() && !ClientPlacement.canPlace(view, index, held);
                    graphics.fill(sx, sy, sx + PanelSlots.SLOT_SIZE, sy + PanelSlots.SLOT_SIZE, denied ? COLOR_SLOT_DENIED : COLOR_SLOT_HOVER);
                }
            }
        }
    }

    /** 037: the layout of the extension rows from what the server last said; an empty one when the backpack has none. */
    private static RowLayout rowLayout(ClientBackpackView view) {
        return new RowLayout(view.baseCapacity(), view.rowView().visibleCategories());
    }

    /**
     * 037: what makes a cell of an extension row look like one: the category's colour over the well, a one pixel line where the
     * cell above or to its left is in another group (so a row that wraps over the columns gets a staircase), and a small square in
     * the corner of the row's first cell. The colour alone does not carry the category: the corner, the faint picture in an empty
     * cell and the tooltip say it too.
     */
    private static void drawRowDecor(GuiGraphics graphics, RowLayout rows, int index, int cols, int sx, int sy, boolean flat) {
        String category = rows.categoryOf(index);
        int size = PanelSlots.SLOT_SIZE;
        graphics.fill(sx + 1, sy + 1, sx + size - 1, sy + size - 1, CategoryStyle.fill(category));
        if (flat) {
            return;
        }
        int edge = CategoryStyle.edge(category);
        if (rows.topEdge(index, cols)) {
            graphics.fill(sx, sy, sx + size, sy + 1, edge);
        }
        if (rows.leftEdge(index, cols)) {
            graphics.fill(sx, sy, sx + 1, sy + size, edge);
        }
        if (rows.isRowStart(index)) {
            graphics.fill(sx + 1, sy + 1, sx + 4, sy + 4, edge);
        }
    }

    /**
     * 037: a faint picture of the category's extension item in an empty cell of its row (what goes here). 038: for a universal row it
     * is the universal extension of the backpack's own tier. Nothing for an unknown category.
     */
    private static void drawGhost(GuiGraphics graphics, String category, int sx, int sy) {
        Item item = ghostItem(category);
        if (item == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(item).getParticleIcon();
        graphics.blit(sx + 1, sy + 1, 0, 16, 16, sprite, 1.0F, 1.0F, 1.0F, GHOST_ALPHA);
    }

    private static Item ghostItem(String category) {
        if (ExtensionCategories.GENERAL.equals(category)) {
            BackpackTier tier = BackpackTier.byCapacity(ClientBackpackView.get().baseCapacity());
            var holder = tier == null ? null : ModItems.UNIVERSAL_EXTENSIONS.get(tier);
            return holder == null ? null : holder.get();
        }
        String id = CategoryStyle.ghostItemId(category);
        var holder = id == null ? null : ModItems.CATEGORY_EXTENSIONS.get(category);
        return holder == null ? null : holder.get();
    }

    /** 037: the tooltip of the extension row cell under the mouse: which category the row is and what it takes. */
    private static void drawRowTooltip(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        if (TagPanelState.get().isOpen() || PickupPanelState.get().isOpen()) {
            return;
        }
        ClientBackpackView view = ClientBackpackView.get();
        RowLayout rows = rowLayout(view);
        if (!rows.hasRows()) {
            return;
        }
        PanelLayout.Hit hit = view.layout().hit(mouseX, mouseY);
        if (hit == null || hit.area() != PanelArea.MAIN || rows.groupOf(hit.index()) < 0) {
            return;
        }
        String category = rows.categoryOf(hit.index());
        // 038: the universal row takes anything, so it says that instead of "only takes X"
        Component description = ExtensionCategories.GENERAL.equals(category)
                ? Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".row.tooltip.general")
                : Component.translatable(
                        "panel." + MCFloatingBackpack.MOD_ID + ".row.tooltip", Component.translatableWithFallback(CategoryNames.langKey(category), category));
        List<FormattedCharSequence> lines = new ArrayList<>(minecraft.font.split(description, 200));
        ItemStack held = cursorStack(minecraft);
        if (!held.isEmpty() && !ClientPlacement.canPlace(view, hit.index(), held)) {
            lines.addAll(minecraft.font.split(
                    Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".row.denied").withColor(COLOR_DENIED_TEXT), 200));
        }
        graphics.renderTooltip(minecraft.font, lines, mouseX, mouseY);
    }

    public static int wellColor(PanelArea area) {
        return switch (area) {
            case MAIN -> COLOR_SLOT;
            case LEFT -> COLOR_SLOT_LEFT;
            case RIGHT -> COLOR_SLOT_RIGHT;
            case BOTTOM -> COLOR_SLOT_BOTTOM;
        };
    }

    /** One pixel frame just inside the slot. */
    private static void drawSelectionBorder(GuiGraphics graphics, int x, int y) {
        drawSelectionBorder(graphics, x, y, PanelSlots.SLOT_SIZE);
    }

    private static void drawSelectionBorder(GuiGraphics graphics, int x, int y, int size) {
        int x2 = x + size;
        int y2 = y + size;
        graphics.fill(x, y, x2, y + 1, COLOR_SELECTED);
        graphics.fill(x, y2 - 1, x2, y2, COLOR_SELECTED);
        graphics.fill(x, y + 1, x + 1, y2 - 1, COLOR_SELECTED);
        graphics.fill(x2 - 1, y + 1, x2, y2 - 1, COLOR_SELECTED);
    }

    public static void drawSlotWell(GuiGraphics graphics, int x, int y, int color) {
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
            if (area == PanelArea.MAIN && (TagPanelState.get().isOpen() || PickupPanelState.get().isOpen())) {
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
        // 042: there is none at the smallest size (B and Esc close the panel)
        if (!state.showClose()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int size = FloatingPanelState.CLOSE_BUTTON_SIZE;
        int x1 = state.titleBar().closeLeft();
        int x2 = x1 + size;
        int y1 = state.getPosY() + 2;
        int y2 = y1 + size;
        graphics.fill(x1, y1, x2, y2, COLOR_CLOSE_BUTTON);
        Component glyph = Component.literal("x");
        int textX = x1 + (size - minecraft.font.width(glyph)) / 2;
        int textY = y1 + (size - minecraft.font.lineHeight) / 2;
        graphics.drawString(minecraft.font, glyph, textX, textY, COLOR_CLOSE_GLYPH, false);
    }

    /**
     * 031: the snapshot button, left of the close button, drawn in code (a small archive box on blue; no texture). Geometry must match
     * {@link FloatingPanelState#hitsSnapshotButton}; it is not drawn when the title bar is too narrow.
     */
    private static void drawSnapshotButton(GuiGraphics graphics, FloatingPanelState state, Minecraft minecraft, int mouseX, int mouseY) {
        TitleBarLayout buttons = state.titleBar();
        // 042: the open search box takes the snapshot button's place
        if (!buttons.hasSnapshot() || SearchState.get().isOpen()) {
            return;
        }
        int size = TitleBarLayout.BUTTON_SIZE;
        int x1 = buttons.snapshotLeft();
        int y1 = state.getPosY() + 2;
        graphics.fill(x1, y1, x1 + size, y1 + size, COLOR_SNAPSHOT_BUTTON);
        // the box: a lid across the top and an outline below it
        graphics.fill(x1 + 2, y1 + 2, x1 + size - 2, y1 + 4, COLOR_CLOSE_GLYPH);
        graphics.fill(x1 + 3, y1 + 4, x1 + 4, y1 + size - 2, COLOR_CLOSE_GLYPH);
        graphics.fill(x1 + size - 4, y1 + 4, x1 + size - 3, y1 + size - 2, COLOR_CLOSE_GLYPH);
        graphics.fill(x1 + 3, y1 + size - 3, x1 + size - 3, y1 + size - 2, COLOR_CLOSE_GLYPH);
        if (state.hitsSnapshotButton(mouseX, mouseY)) {
            graphics.renderTooltip(
                    minecraft.font, Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".snapshot.tooltip"), mouseX, mouseY);
        }
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
