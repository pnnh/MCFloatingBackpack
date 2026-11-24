package pnnh.mcfloatingbackpack.client.panel.combined;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.client.panel.combined.CombinedBarLayout.Rect;

/**
 * 065: draws the combined bar above the floating panel: the strip of tabs (066: the "all" tab first, with the "+n" of backpacks that are not
 * connected; no title bar any more), then one tab for each backpack: a swatch in the tab's colour, the backpack's tier icon, and its name or number.
 * Called inside the panel's own pose (above the screen, below the panel's tooltips), so the geometry is in plain GUI pixels.
 */
@OnlyIn(Dist.CLIENT)
public final class CombinedBarRenderer {
    private static final int COLOR_BAR = 0xFFC6C6C6;
    private static final int COLOR_EDGE_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_EDGE_DARK = 0xFF555555;
    private static final int COLOR_OUTLINE = 0xFF000000;
    private static final int COLOR_TAB = 0xFF9E9E9E;
    private static final int COLOR_TAB_HOVER = 0xFFB4B4B4;
    private static final int COLOR_TAB_SELECTED = 0xFFF2F2F2;
    private static final int COLOR_TEXT = 0xFF202020;
    private static final int COLOR_TITLE = 0xFF404040;
    private static final int COLOR_BADGE = 0xFFB33A3A;
    private static final int COLOR_BADGE_TEXT = 0xFFFFFFFF;
    private static final int COLOR_ACTIVE_PIP = 0xFFFFD34D;
    private static final int COLOR_ARROW = 0xFF3A3A3A;
    private static final int COLOR_LOADING = 0x60000000;

    private CombinedBarRenderer() {
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!CombinedBars.shown()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        ClientCombinedState state = ClientCombinedState.get();
        state.expireLoading();
        CombinedBarLayout layout = CombinedBars.layout();
        if (state.takeRevealSelected()) {
            state.setTabScroll(layout.scrollToShow(state.selectedTabIndex(), state.tabScroll()));
        }
        state.setTabScroll(layout.clampScroll(state.tabScroll()));
        drawTabs(graphics, font, layout, state, mouseX, mouseY);
        if (state.loading()) {
            Rect bar = layout.tabBarRect();
            graphics.fill(bar.x(), bar.y(), bar.x() + bar.w(), bar.y() + bar.h(), COLOR_LOADING);
        }
    }

    private static void bevel(GuiGraphics graphics, Rect r, int fill) {
        graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), COLOR_OUTLINE);
        graphics.fill(r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, fill);
        graphics.fill(r.x() + 1, r.y() + 1, r.x() + r.w() - 1, r.y() + 2, COLOR_EDGE_LIGHT);
        graphics.fill(r.x() + 1, r.y() + 1, r.x() + 2, r.y() + r.h() - 1, COLOR_EDGE_LIGHT);
        graphics.fill(r.x() + 1, r.y() + r.h() - 2, r.x() + r.w() - 1, r.y() + r.h() - 1, COLOR_EDGE_DARK);
        graphics.fill(r.x() + r.w() - 2, r.y() + 1, r.x() + r.w() - 1, r.y() + r.h() - 1, COLOR_EDGE_DARK);
    }

    /**
     * 066: the bar is the top of the panel, not a box of its own: the panel's outline goes round both, the black line that was the panel's top edge
     * is covered, and the bar has the panel's own colour and light and dark edges.
     */
    private static void drawBarBody(GuiGraphics graphics, Rect bar) {
        int x = bar.x();
        int y = bar.y();
        int right = x + bar.w();
        int bottom = y + bar.h();
        // the outline above and at both sides, continuing the panel's
        graphics.fill(x - 1, y - 1, right + 1, bottom, COLOR_OUTLINE);
        graphics.fill(x, y, right, bottom, COLOR_BAR);
        graphics.fill(x, y, right, y + 1, COLOR_EDGE_LIGHT);
        graphics.fill(x, y, x + 1, bottom, COLOR_EDGE_LIGHT);
        graphics.fill(right - 1, y, right, bottom, COLOR_EDGE_DARK);
    }

    private static void drawIcon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.75F, 0.75F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static void drawTabs(
            GuiGraphics graphics, Font font, CombinedBarLayout layout, ClientCombinedState state, int mouseX, int mouseY) {
        Rect bar = layout.tabBarRect();
        drawBarBody(graphics, bar);
        Rect area = layout.tabArea();
        graphics.enableScissor(area.x(), area.y(), area.x() + area.w(), area.y() + area.h());
        int hover = layout.hitTab(mouseX, mouseY, state.tabScroll());
        int selectedTab = state.selectedTabIndex();
        // tab 0 is the "all" tab, the member tabs follow
        for (int t = 0; t < layout.tabCount(); t++) {
            Rect tab = layout.tabRect(t, state.tabScroll());
            if (tab.x() + tab.w() < area.x() || tab.x() > area.x() + area.w()) {
                continue;
            }
            boolean selected = t == selectedTab;
            boolean lit = t == hover || (t > 0 && t - 1 == state.hoverMember());
            int fill = selected ? COLOR_TAB_SELECTED : (lit ? COLOR_TAB_HOVER : COLOR_TAB);
            graphics.fill(tab.x(), tab.y() + 1, tab.x() + tab.w(), tab.y() + tab.h() - 1, COLOR_OUTLINE);
            graphics.fill(tab.x() + 1, tab.y() + 2, tab.x() + tab.w() - 1, tab.y() + tab.h() - 1, fill);
            if (t == 0) {
                drawAllTab(graphics, font, tab, state, selected);
                continue;
            }
            int i = t - 1;
            ClientCombinedState.MemberView member = state.members().get(i);
            // the tab's colour: a swatch on the left, and a stripe along the bottom of the selected one
            graphics.fill(tab.x() + 2, tab.y() + 3, tab.x() + 5, tab.y() + tab.h() - 2, CombinedColors.argb(i));
            if (selected) {
                graphics.fill(tab.x() + 1, tab.y() + tab.h() - 2, tab.x() + tab.w() - 1, tab.y() + tab.h() - 1, CombinedColors.argb(i));
            }
            drawIcon(graphics, ModItems.stackFor(tierOf(member.tier())), tab.x() + 7, tab.y() + 2);
            graphics.drawString(font, CombinedBars.shown(font, member), tab.x() + 21, tab.y() + 5, COLOR_TEXT, false);
            if (state.isBActive(member)) {
                graphics.fill(tab.x() + tab.w() - 5, tab.y() + 3, tab.x() + tab.w() - 2, tab.y() + 6, COLOR_ACTIVE_PIP);
            }
        }
        graphics.disableScissor();
        if (layout.overflows()) {
            drawArrow(graphics, layout.leftArrow(), -1);
            drawArrow(graphics, layout.rightArrow(), 1);
        }
    }

    /** 066: the "all" tab: the combined backpack's icon, the word, and the "+n" of the backpacks that are not connected. */
    private static void drawAllTab(GuiGraphics graphics, Font font, Rect tab, ClientCombinedState state, boolean selected) {
        drawIcon(graphics, new ItemStack(ModItems.COMBINED_BACKPACKS.get(tierOf(state.combinedTier())).get()), tab.x() + 4, tab.y() + 2);
        graphics.drawString(font, CombinedBars.allLabel(), tab.x() + 18, tab.y() + 5, COLOR_TEXT, false);
        String badge = CombinedBars.badge(state);
        if (!badge.isEmpty()) {
            int w = font.width(badge) + 6;
            int right = tab.x() + tab.w() - 3;
            graphics.fill(right - w, tab.y() + 3, right, tab.y() + tab.h() - 2, COLOR_BADGE);
            graphics.drawString(font, badge, right - w + 3, tab.y() + 5, COLOR_BADGE_TEXT, false);
        }
        if (selected) {
            graphics.fill(tab.x() + 1, tab.y() + tab.h() - 2, tab.x() + tab.w() - 1, tab.y() + tab.h() - 1, COLOR_ACTIVE_PIP);
        }
    }

    private static void drawArrow(GuiGraphics graphics, Rect r, int direction) {
        int cx = r.x() + r.w() / 2;
        int cy = r.y() + r.h() / 2;
        for (int i = 0; i < 4; i++) {
            int x = direction < 0 ? cx - 2 + i : cx + 2 - i;
            graphics.fill(x, cy - i, x + 1, cy + i + 1, COLOR_ARROW);
        }
    }

    /** The tooltip of the tab or the badge under the mouse, if any (drawn after everything else of the panel). */
    public static void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!CombinedBars.shown()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientCombinedState state = ClientCombinedState.get();
        CombinedBarLayout layout = CombinedBars.layout();
        int tab = layout.hitTab(mouseX, mouseY, state.tabScroll());
        if (tab == 0) {
            // 066: the "all" tab: what it is, the tier of the combined backpack, and why some backpacks are not connected
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.mcfloatingbackpack.combined.all.tooltip", state.members().size()));
            lines.add(Component.translatable(
                    "gui.mcfloatingbackpack.combined.all.tier",
                    Component.translatable("item.mcfloatingbackpack." + tierOf(state.combinedTier()).name().toLowerCase(java.util.Locale.ROOT) + "_combined_backpack")));
            if (state.skippedTier() > 0) {
                lines.add(Component.translatable("gui.mcfloatingbackpack.combined.skipped_tier", state.skippedTier()));
            }
            if (state.skippedNewer() > 0) {
                lines.add(Component.translatable("gui.mcfloatingbackpack.combined.skipped_newer", state.skippedNewer()));
            }
            if (state.skippedOverCap() > 0) {
                lines.add(Component.translatable("gui.mcfloatingbackpack.combined.skipped_cap", state.skippedOverCap()));
            }
            graphics.renderTooltip(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (tab > 0) {
            ClientCombinedState.MemberView member = state.members().get(tab - 1);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(CombinedBars.label(member)));
            lines.add(placeText(member.ref()));
            lines.add(Component.translatable(
                    "gui.mcfloatingbackpack.combined.capacity", tierOf(member.tier()).capacity()));
            if (state.isBActive(member)) {
                lines.add(Component.translatable("gui.mcfloatingbackpack.combined.b_active"));
            }
            graphics.renderTooltip(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    /** 066: "From #A3F2 (inventory slot 5)" for the cell of member {@code member} of the "all" tab, or null if there is no such member. */
    public static Component fromText(int member) {
        ClientCombinedState state = ClientCombinedState.get();
        if (member < 0 || member >= state.members().size()) {
            return null;
        }
        ClientCombinedState.MemberView view = state.members().get(member);
        Component square = Component.literal("\u25A0").withStyle(style -> style.withColor(CombinedColors.argb(member) & 0x00FFFFFF));
        return Component.translatable(
                "gui.mcfloatingbackpack.combined.all.from",
                Component.empty().append(square).append(Component.literal(" " + CombinedBars.label(view))),
                placeText(view.ref()));
    }

    /** Where the backpack is, in words. */
    static Component placeText(BackpackRef ref) {
        return switch (ref.source()) {
            case CHEST -> Component.translatable("gui.mcfloatingbackpack.combined.place.chest");
            case CURIOS, CURIOS_DEDICATED, CURIOS_CHARM -> Component.translatable("gui.mcfloatingbackpack.combined.place.curios");
            case ACCESSORIES, ACCESSORIES_DEDICATED, ACCESSORIES_CHARM -> Component.translatable("gui.mcfloatingbackpack.combined.place.accessories");
            case OFF_HAND -> Component.translatable("gui.mcfloatingbackpack.combined.place.offhand");
            default -> Component.translatable("gui.mcfloatingbackpack.combined.place.inventory", ref.invIndex() + 1);
        };
    }

    private static BackpackTier tierOf(int ordinal) {
        BackpackTier[] tiers = BackpackTier.values();
        return tiers[Math.min(Math.max(ordinal, 0), tiers.length - 1)];
    }
}
