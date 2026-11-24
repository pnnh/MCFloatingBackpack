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
import pnnh.mcfloatingbackpack.client.compat.NetworkHeld;
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
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelState;
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
import pnnh.mcfloatingbackpack.item.TankItem;
import pnnh.mcfloatingbackpack.tank.TankBar;
import pnnh.mcfloatingbackpack.tank.TankCatalog;

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
    /** 046: a main-area cell with nothing to show while a search is on: darker than the empty cell above, same bevel. */
    private static final int COLOR_SLOT_GREY = 0xFF606060;
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
    /** 047: the sort button: violet, apart from the green magnifier, the blue snapshot button and the red close button. */
    private static final int COLOR_SORT_BUTTON = 0xFF7B5FB5;
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
        // 065: the combined bars hang above the panel
        pnnh.mcfloatingbackpack.client.panel.combined.CombinedBarRenderer.render(graphics, mouseX, mouseY);
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
        // 047: the sort button, left of the magnifier
        drawSortButton(graphics, state, minecraft, mouseX, mouseY);
        // 044: /hidden: the main area has no cells, its summary of the hidden items is written there; the whole 037 hint is the tooltip
        PanelLayout.Rect mainArea = ClientBackpackView.get().layout().rect(PanelArea.MAIN);
        // (not over the tag panel or the blacklist page, which cover the main area themselves)
        if (SearchState.get().isOpen() && SearchState.get().command().type() == SearchQuery.Command.Type.HIDDEN
                && !TagPanelState.get().isOpen() && !PickupPanelState.get().isOpen()) {
            SearchRenderer.drawHiddenSummary(graphics, minecraft, mainArea);
            List<HiddenHint.Line> hiddenLines = HiddenHint.lines(ClientBackpackView.get().rowView().hidden());
            // 048: and the liquids that have no tank
            List<pnnh.mcfloatingbackpack.tank.HiddenLiquids.Line> hiddenLiquids = ClientBackpackView.get().hiddenLiquids();
            if ((!hiddenLines.isEmpty() || !hiddenLiquids.isEmpty()) && mouseX >= mainArea.x() && mouseX < mainArea.x() + mainArea.w()
                    && mouseY >= mainArea.y() && mouseY < mainArea.y() + mainArea.h()) {
                drawHiddenHint(graphics, minecraft, hiddenLines, hiddenLiquids, mouseX, mouseY);
            }
        }
        // 042: the magnifier, or the open search box in place of the number and the buttons
        SearchRenderer.render(graphics, state, minecraft, mouseX, mouseY);
        drawResizeHandle(graphics, state);
        drawTitleIcon(graphics, state, minecraft, mouseX, mouseY);
        // 045: the tooltip of the item in the cell under the mouse; only when there is none the cell may have the extension row's
        if (!drawItemTooltip(graphics, minecraft, mouseX, mouseY)) {
            drawRowTooltip(graphics, minecraft, mouseX, mouseY);
        }
        // 065: the tooltip of the tab under the mouse
        pnnh.mcfloatingbackpack.client.panel.combined.CombinedBarRenderer.renderTooltip(graphics, mouseX, mouseY);
        // 021: the trash can panel hangs off the right edge, outside the rectangle everything above is drawn in.
        if (TrashPanelState.get().isOpen()) {
            TrashPanelRenderer.render(graphics, mouseX, mouseY);
        }
        // 053: the player page of the mobile jukebox hangs at the same place (only one of the two is ever open).
        if (JukeboxPanelState.get().isOpen()) {
            JukeboxPanelRenderer.render(graphics, mouseX, mouseY);
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
        if (cursor.isEmpty()) {
            // 060: an entry picked up on a network terminal's own cursor (Integrated Terminals) is not the menu's carried stack; the terminal draws
            // it under the panel, so it is drawn again here, above the panel, at the same place.
            cursor = NetworkHeldDrop.heldOn(minecraft, minecraft.screen).map(NetworkHeld::asStack).orElse(ItemStack.EMPTY);
        }
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
        // 066: on the "all" tab the icon is the combined backpack's, and there is no number or hidden-content dot (they belong to one backpack)
        if (state.isAllMode()) {
            BackpackTier combinedTier = BackpackTier.values()[Math.min(
                    Math.max(pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedState.get().combinedTier(), 0), BackpackTier.values().length - 1)];
            graphics.renderItem(new ItemStack(ModItems.COMBINED_BACKPACKS.get(combinedTier).get()), state.getPosX() + 4, state.getPosY() + 1);
            if (state.hitsTitleIcon(mouseX, mouseY)) {
                graphics.renderTooltip(
                        minecraft.font,
                        Component.translatable("item.mcfloatingbackpack." + combinedTier.name().toLowerCase(java.util.Locale.ROOT) + "_combined_backpack"),
                        mouseX,
                        mouseY);
            }
            return;
        }
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
        // 048: the liquids the backpack holds with no tank for them are hidden content too: the same dot
        List<pnnh.mcfloatingbackpack.tank.HiddenLiquids.Line> hiddenLiquids = ClientBackpackView.get().hiddenLiquids();
        boolean anyHidden = !hidden.isEmpty() || !hiddenLiquids.isEmpty();
        boolean overDot = anyHidden && TitleDotLayout.hits(state.getPosX(), state.getPosY(), mouseX, mouseY);
        if (anyHidden) {
            drawHiddenDot(graphics, state);
        }
        if (overDot) {
            drawHiddenHint(graphics, minecraft, hidden, hiddenLiquids, mouseX, mouseY);
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
    private static void drawHiddenHint(
            GuiGraphics graphics,
            Minecraft minecraft,
            List<HiddenHint.Line> lines,
            List<pnnh.mcfloatingbackpack.tank.HiddenLiquids.Line> liquids,
            int mouseX,
            int mouseY) {
        List<FormattedCharSequence> out = new ArrayList<>();
        // 048: the words depend on whether items, liquids or both are hidden
        boolean hasItems = !lines.isEmpty();
        boolean hasLiquids = !liquids.isEmpty();
        out.addAll(minecraft.font.split(
                Component.translatable("panel." + MCFloatingBackpack.MOD_ID
                        + pnnh.mcfloatingbackpack.client.panel.search.HiddenTextKeys.title(hasItems, hasLiquids)), 220));
        for (HiddenHint.Line line : lines) {
            // A category this version does not know (made by a newer version) has no language entry: show its id.
            Component name = Component.translatableWithFallback(CategoryNames.langKey(line.category()), line.category());
            out.addAll(minecraft.font.split(
                    Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".hidden.line", name, line.cells()), 220));
        }
        for (pnnh.mcfloatingbackpack.tank.HiddenLiquids.Line liquid : liquids) {
            Component name = Component.translatable("fluid." + MCFloatingBackpack.MOD_ID + "." + liquid.kind().id());
            out.addAll(minecraft.font.split(
                    Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".hidden.liquid.line", name, liquid.amountMb()), 220));
        }
        for (String hintKey : pnnh.mcfloatingbackpack.client.panel.search.HiddenTextKeys.hints(hasItems, hasLiquids)) {
            out.addAll(minecraft.font.split(
                    Component.translatable("panel." + MCFloatingBackpack.MOD_ID + hintKey).withStyle(ChatFormatting.GRAY), 220));
        }
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
        // 066: on the "all" tab every cell is washed in its backpack's colour, and the cell under the mouse lights its backpack's tab
        boolean allTab = pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedState.get().isAllSelected();
        pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView combinedView = pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView.get();
        pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedState.get().setHoverMember(
                allTab && hovered != null && hovered.area() == PanelArea.MAIN ? combinedView.memberOf(hovered.index()) : -1);
        Preview<ItemStack> preview = PanelDragInput.preview();
        PanelSelection selection = PanelSelection.get();
        // 037: the rows of the category extensions, once per frame; without rows none of the code that uses it runs.
        RowLayout rows = rowLayout(view);
        // 066: on the "all" tab every member has its own rows: a layout for each, asked by the member a cell belongs to
        RowLayout[] memberRows = allTab ? memberRowLayouts(combinedView) : null;
        boolean anyRows = rows.hasRows();
        if (memberRows != null) {
            for (RowLayout memberLayout : memberRows) {
                anyRows |= memberLayout.hasRows();
            }
        }
        ItemStack held = anyRows ? cursorStack(minecraft) : ItemStack.EMPTY;
        // 042: while a query is on the main area shows the found slots packed together, so the rows' lines and corners (which
        // say where a row begins and ends in the full grid) are left out; and the cells that match are framed
        SearchState search = SearchState.get();
        // 066: a sorted grid no longer has a row's cells next to each other: only the colour is drawn then, as while searching
        boolean searching = search.isFiltering() || (allTab && combinedView.sortOrder() != null);
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
                    // 046: while a search is on, the main area's cells that hold no result are drawn as grey cells (they have no slot
                    // behind them, so nothing can be done with them); otherwise they stay plain panel background
                    if (GreyCellRules.greyed(searching, TagPanelState.get().isOpen() || PickupPanelState.get().isOpen(), area, index)) {
                        drawSlotWell(graphics, layout.cellX(area, ordinal), layout.cellY(area, ordinal), COLOR_SLOT_GREY);
                    }
                    continue;
                }
                int sx = layout.cellX(area, ordinal);
                int sy = layout.cellY(area, ordinal);
                drawSlotWell(graphics, sx, sy, wellColor(area));
                if (allTab && area == PanelArea.MAIN) {
                    int member = combinedView.memberOf(index);
                    if (member >= 0) {
                        graphics.fill(sx + 1, sy + 1, sx + PanelSlots.SLOT_SIZE - 1, sy + PanelSlots.SLOT_SIZE - 1,
                                pnnh.mcfloatingbackpack.client.panel.combined.CombinedColors.wash(member));
                    }
                }
                ItemStack stack = area == PanelArea.MAIN ? view.getStackInSlot(index) : view.getToolStack(area, index);
                RowLayout cellRows = rows;
                int rowIndex = index;
                if (allTab && area == PanelArea.MAIN) {
                    int owner = combinedView.memberOf(index);
                    cellRows = owner >= 0 && memberRows != null ? memberRows[owner] : rows;
                    rowIndex = combinedView.localOf(index);
                }
                boolean ofARow = area == PanelArea.MAIN && cellRows.groupOf(rowIndex) >= 0;
                if (ofARow && allTab && !searching) {
                    drawRowDecorAll(graphics, layout, combinedView, memberRows, area, ordinal, index, sx, sy);
                } else if (ofARow) {
                    drawRowDecor(graphics, cellRows, rowIndex, layout.mainCols(), sx, sy, searching);
                }
                // 013 drag preview and 014 selection only exist in the main area.
                int previewIndex = area == PanelArea.MAIN && preview != null ? preview.slots().indexOf(index) : -1;
                if (ofARow && stack.isEmpty() && previewIndex < 0) {
                    drawGhost(graphics, cellRows.categoryOf(rowIndex), sx, sy, cellRows.baseCapacity());
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
                    // 048: a tank in the bottom area (the only place it counts) shows how full it is
                    if (area == PanelArea.BOTTOM) {
                        drawTankBar(graphics, view, stack, sx, sy);
                    }
                }
                // 065: a mobile tool in the combined panel on a backpack the B key does not open does nothing: it is drawn greyed out
                if (area == PanelArea.RIGHT
                        && !stack.isEmpty()
                        && FloatingPanelInput.isMobileTool(stack)
                        && pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedState.get().toolsDisabled()) {
                    graphics.fill(sx + 1, sy + 1, sx + PanelSlots.SLOT_SIZE - 1, sy + PanelSlots.SLOT_SIZE - 1, 0xB0606060);
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

    /**
     * 048: the level bar of a tank: a thin vertical bar at the cell's left edge, in the colour of the liquid, drawn on the same layer as
     * the durability bar of a vanilla item (over the item). Nothing is drawn for an empty tank or for a stack that is not a tank.
     */
    private static void drawTankBar(GuiGraphics graphics, ClientBackpackView view, ItemStack stack, int sx, int sy) {
        TankCatalog.Tank tank = TankItem.tankOf(stack);
        if (tank == null) {
            return;
        }
        int amount = view.fluids().amount(tank.kind());
        int fill = TankBar.fillPixels(amount, TankCatalog.capacityMb(tank.tier()), TankBar.HEIGHT);
        if (fill <= 0) {
            return;
        }
        int x = sx + TankBar.OFFSET;
        int y = sy + TankBar.OFFSET;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.fill(x, y, x + TankBar.WIDTH, y + TankBar.HEIGHT, TankBar.COLOR_BACKING);
        graphics.fill(x, y + TankBar.HEIGHT - fill, x + TankBar.WIDTH, y + TankBar.HEIGHT, tank.kind().color());
        graphics.pose().popPose();
    }

    /** 048: the line a tank's tooltip gets in the bottom area: which liquid, how much, how much it holds; or that it is empty. */
    private static Component tankTooltipLine(ClientBackpackView view, TankCatalog.Tank tank) {
        Component liquid = Component.translatable("fluid.mcfloatingbackpack." + tank.kind().id());
        int amount = view.fluids().amount(tank.kind());
        Component line = amount > 0
                ? Component.translatable("tooltip.mcfloatingbackpack.tank.amount", liquid, amount, TankCatalog.capacityMb(tank.tier()))
                : Component.translatable("tooltip.mcfloatingbackpack.tank.empty", liquid);
        return line.copy().withStyle(ChatFormatting.AQUA);
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
    private static void drawGhost(GuiGraphics graphics, String category, int sx, int sy, int baseCapacity) {
        Item item = ghostItem(category, baseCapacity);
        if (item == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(item).getParticleIcon();
        graphics.blit(sx + 1, sy + 1, 0, 16, 16, sprite, 1.0F, 1.0F, 1.0F, GHOST_ALPHA);
    }

    /**
     * 066: the same decoration on the "all" tab, where the backpacks' cells run on from one another and a row can start in the middle of a line:
     * the lines are drawn where the cell above or to the left (as shown) is of another group of the same or another backpack.
     */
    private static void drawRowDecorAll(
            GuiGraphics graphics,
            PanelLayout layout,
            pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView combined,
            RowLayout[] memberRows,
            PanelArea area,
            int ordinal,
            int flat,
            int sx,
            int sy) {
        int cols = layout.mainCols();
        int member = combined.memberOf(flat);
        RowLayout rows = memberRows[member];
        int local = combined.localOf(flat);
        String category = rows.categoryOf(local);
        int size = PanelSlots.SLOT_SIZE;
        graphics.fill(sx + 1, sy + 1, sx + size - 1, sy + size - 1, CategoryStyle.fill(category));
        int edge = CategoryStyle.edge(category);
        int position = layout.mainPosition(ordinal);
        long key = groupKey(combined, memberRows, flat);
        if (position < cols || groupKey(combined, memberRows, layout.slotAtPosition(position - cols)) != key) {
            graphics.fill(sx, sy, sx + size, sy + 1, edge);
        }
        if (position % cols == 0 || groupKey(combined, memberRows, layout.slotAtPosition(position - 1)) != key) {
            graphics.fill(sx, sy, sx + 1, sy + size, edge);
        }
        if (rows.isRowStart(local)) {
            graphics.fill(sx + 1, sy + 1, sx + 4, sy + 4, edge);
        }
    }

    /** Which row of which backpack a cell is in (the main cells of a backpack are one group), or -1 for no cell. */
    private static long groupKey(
            pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView combined, RowLayout[] memberRows, int flat) {
        if (flat < 0) {
            return -1;
        }
        int member = combined.memberOf(flat);
        if (member < 0) {
            return -1;
        }
        return member * 1000L + memberRows[member].groupOf(combined.localOf(flat)) + 2;
    }

    /** 066: the rows of each member of the "all" tab, as the member told the client (for the colour, the corner and the faint picture of a row's cells). */
    private static RowLayout[] memberRowLayouts(pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView combined) {
        RowLayout[] layouts = new RowLayout[combined.memberCount()];
        for (int i = 0; i < layouts.length; i++) {
            pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView.Slice slice = combined.slice(i);
            layouts[i] = slice == null
                    ? new RowLayout(0, List.of())
                    : new RowLayout(slice.rows().baseCapacity(), slice.rows().visibleCategories());
        }
        return layouts;
    }

    private static Item ghostItem(String category, int baseCapacity) {
        if (ExtensionCategories.GENERAL.equals(category)) {
            BackpackTier tier = BackpackTier.byCapacity(baseCapacity);
            var holder = tier == null ? null : ModItems.UNIVERSAL_EXTENSIONS.get(tier);
            return holder == null ? null : holder.get();
        }
        String id = CategoryStyle.ghostItemId(category);
        var holder = id == null ? null : ModItems.CATEGORY_EXTENSIONS.get(category);
        return holder == null ? null : holder.get();
    }

    /**
     * 045: the vanilla tooltip of the item in the cell under the mouse (the main area's cells, which a search maps to the found ones,
     * and the three tool areas), unless {@link ItemTooltipRules} says no. Returns whether it drew one.
     */
    private static boolean drawItemTooltip(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        ClientBackpackView view = ClientBackpackView.get();
        PanelLayout.Hit hit = view.layout().hit(mouseX, mouseY);
        if (hit == null) {
            return false;
        }
        ItemStack stack = hit.area() == PanelArea.MAIN ? view.getStackInSlot(hit.index()) : view.getToolStack(hit.area(), hit.index());
        boolean mainCovered = TagPanelState.get().isOpen() || PickupPanelState.get().isOpen();
        // 066: on the "all" tab the tooltip of a cell ends with where it is: which backpack and where that is (an empty cell says it too)
        if (hit.area() == PanelArea.MAIN
                && pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedState.get().isAllSelected()
                && !mainCovered
                && PanelDragInput.preview() == null
                && !TrashConfirmState.get().isPending()) {
            List<Component> lines = new ArrayList<>();
            if (!stack.isEmpty() && cursorStack(minecraft).isEmpty()) {
                lines.addAll(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, stack));
            }
            Component from = pnnh.mcfloatingbackpack.client.panel.combined.CombinedBarRenderer.fromText(
                    pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView.get().memberOf(hit.index()));
            if (from != null) {
                lines.add(from.copy().withStyle(ChatFormatting.GRAY));
            }
            if (!lines.isEmpty()) {
                graphics.renderTooltip(minecraft.font, lines, java.util.Optional.empty(), mouseX, mouseY);
                return true;
            }
        }
        if (!ItemTooltipRules.shouldShow(
                hit.area(),
                mainCovered,
                !cursorStack(minecraft).isEmpty(),
                PanelDragInput.preview() != null,
                TrashConfirmState.get().isPending(),
                !stack.isEmpty())) {
            return false;
        }
        // 048: a tank in the bottom area (where it counts) also says what it holds
        TankCatalog.Tank tank = hit.area() == PanelArea.BOTTOM ? TankItem.tankOf(stack) : null;
        if (tank != null) {
            List<Component> lines = new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, stack));
            lines.add(tankTooltipLine(view, tank));
            graphics.renderTooltip(minecraft.font, lines, java.util.Optional.empty(), mouseX, mouseY);
            return true;
        }
        graphics.renderTooltip(minecraft.font, stack, mouseX, mouseY);
        return true;
    }

    /** 037: the tooltip of the extension row cell under the mouse: which category the row is and what it takes. */
    private static void drawRowTooltip(GuiGraphics graphics, Minecraft minecraft, int mouseX, int mouseY) {
        if (TagPanelState.get().isOpen() || PickupPanelState.get().isOpen()) {
            return;
        }
        ClientBackpackView view = ClientBackpackView.get();
        RowLayout rows = rowLayout(view);
        PanelLayout.Hit hit = view.layout().hit(mouseX, mouseY);
        int rowIndex = hit == null ? -1 : hit.index();
        // 066: on the "all" tab the row is the one of the member the cell is in
        if (hit != null && pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedState.get().isAllSelected()) {
            pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView combined = pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView.get();
            pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView.Slice slice = combined.sliceOfFlat(hit.index());
            rows = slice == null ? new RowLayout(0, List.of()) : new RowLayout(slice.rows().baseCapacity(), slice.rows().visibleCategories());
            rowIndex = combined.localOf(hit.index());
        }
        if (!rows.hasRows()) {
            return;
        }
        if (hit == null || hit.area() != PanelArea.MAIN || rows.groupOf(rowIndex) < 0) {
            return;
        }
        String category = rows.categoryOf(rowIndex);
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
        int y1 = state.getPosY() + TitleBarLayout.BUTTON_TOP;
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
        if (!buttons.hasSnapshot() || SearchState.get().isOpen() || state.isAllMode()) {
            return;
        }
        int size = TitleBarLayout.BUTTON_SIZE;
        int x1 = buttons.snapshotLeft();
        int y1 = state.getPosY() + TitleBarLayout.BUTTON_TOP;
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

    /**
     * 047: the sort button, left of the magnifier, drawn in code (three bars getting shorter, on a violet button; no texture). Geometry
     * must match {@link FloatingPanelState#hitsSortButton}; the open search box takes its place.
     */
    private static void drawSortButton(GuiGraphics graphics, FloatingPanelState state, Minecraft minecraft, int mouseX, int mouseY) {
        if (SearchState.get().isOpen()) {
            return;
        }
        int size = TitleBarLayout.BUTTON_SIZE;
        int x1 = state.titleBar().sortLeft();
        int y1 = state.getPosY() + TitleBarLayout.BUTTON_TOP;
        graphics.fill(x1, y1, x1 + size, y1 + size, COLOR_SORT_BUTTON);
        graphics.fill(x1 + 2, y1 + 3, x1 + size - 2, y1 + 4, COLOR_CLOSE_GLYPH);
        graphics.fill(x1 + 2, y1 + 5, x1 + size - 4, y1 + 6, COLOR_CLOSE_GLYPH);
        graphics.fill(x1 + 2, y1 + 7, x1 + size - 6, y1 + 8, COLOR_CLOSE_GLYPH);
        graphics.fill(x1 + 2, y1 + 9, x1 + 4, y1 + 10, COLOR_CLOSE_GLYPH);
        if (state.hitsSortButton(mouseX, mouseY)) {
            graphics.renderTooltip(
                    minecraft.font, Component.translatable("panel." + MCFloatingBackpack.MOD_ID + ".sort.tooltip"), mouseX, mouseY);
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
