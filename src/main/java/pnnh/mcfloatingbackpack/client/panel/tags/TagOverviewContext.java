package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewModel.Row;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;
import pnnh.mcfloatingbackpack.inventory.tags.TagBook;
import pnnh.mcfloatingbackpack.inventory.tags.TagCandidates;
import pnnh.mcfloatingbackpack.inventory.tags.TagMatcher;

/**
 * 025: ties the pure overview classes to the game: the list's rows (sorted once and kept until the tags, the filter or the
 * language change), what an item is called and drawn as, the drop-downs' entries and the layouts. Used by both the renderer
 * and the input, so they always agree on where things are.
 */
@OnlyIn(Dist.CLIENT)
public final class TagOverviewContext {
    /** The back button of the detail page, in the status row's left corner. */
    public static final TagPanelLayout.Rect BACK_RECT = new TagPanelLayout.Rect(2, 2, 14, 14);

    private static ItemTags cachedTags;
    private static String cachedFilter;
    private static String cachedLanguage;
    private static List<Row<ResourceLocation>> cachedRows = List.of();

    private TagOverviewContext() {
    }

    public static boolean hasTool() {
        return ClientBackpackView.get().hasTagManager();
    }

    // ---- items

    /** Whether the item is still in the game; one that is not can be looked at and trimmed, not given new tags. */
    public static boolean registered(ResourceLocation id) {
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    /** What an item is called: its name in the current language, or for an item no longer in the game its id. */
    public static String nameOf(ResourceLocation id) {
        return registered(id) ? BuiltInRegistries.ITEM.get(id).getDescription().getString() : id.toString();
    }

    /** The stack to draw as the item's icon, or empty for an item no longer in the game. */
    public static ItemStack stackFor(ResourceLocation id) {
        return registered(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    /** The item the detail page shows, or null. */
    public static ResourceLocation detailKey() {
        String id = TagOverviewState.get().detailItem();
        return id == null ? null : ResourceLocation.tryParse(id);
    }

    // ---- the list

    /** The rows under the current filter, sorted by name; recomputed only when the tags, the filter or the language change. */
    public static List<Row<ResourceLocation>> rows() {
        ItemTags tags = ClientBackpackView.get().tags();
        String filter = TagOverviewState.get().filter();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (tags != cachedTags || !Objects.equals(filter, cachedFilter) || !Objects.equals(language, cachedLanguage)) {
            cachedRows = TagOverviewModel.rows(tags.toBook(), TagOverviewContext::nameOf, ResourceLocation::toString, filter);
            cachedTags = tags;
            cachedFilter = filter;
            cachedLanguage = language;
        }
        return cachedRows;
    }

    /** Every tag this backpack has put on any item, each once. */
    private static List<String> usedTags() {
        Set<String> used = new LinkedHashSet<>();
        for (List<String> tags : ClientBackpackView.get().tags().map().values()) {
            used.addAll(tags);
        }
        return new ArrayList<>(used);
    }

    private static List<String> match(List<String> ids, String typed) {
        List<TagMatcher.Entry> entries = new ArrayList<>();
        for (String id : ids) {
            entries.add(new TagMatcher.Entry(id, TagPanelContext.label(id)));
        }
        return TagMatcher.match(typed, entries);
    }

    /** The tag ids the filter's drop-down offers right now: fuzzy matches of the typed text, only while editing. */
    public static List<String> filterDropdown() {
        TagOverviewState state = TagOverviewState.get();
        if (!state.isFilterEditing()) {
            return List.of();
        }
        return match(TagCandidates.of(PresetTags.ids(), usedTags(), List.of()), state.filterInput().text());
    }

    /** The width of every row's clear button: the wider of its two labels plus some room. */
    private static int clearWidth(Font font) {
        return Math.max(
                        font.width(Component.translatable("overview.mcfloatingbackpack.clear")),
                        font.width(Component.translatable("overview.mcfloatingbackpack.clear.confirm")))
                + 8;
    }

    public static TagOverviewLayout layout(int width, int height, int rowCount) {
        TagOverviewState state = TagOverviewState.get();
        Font font = Minecraft.getInstance().font;
        TagOverviewLayout layout = TagOverviewLayout.of(
                width,
                height,
                rowCount,
                state.listScroll(),
                state.filter() != null,
                state.isFilterEditing(),
                filterDropdown().size(),
                clearWidth(font));
        if (state.listScroll() != layout.scroll()) {
            state.setListScroll(layout.scroll());
        }
        return layout;
    }

    // ---- the detail page

    /** The detail page's item as a one-category "selection", which gives its tags, whether one can be added and 022's protection. */
    public static TagPanelModel<ResourceLocation> detailModel() {
        ResourceLocation key = detailKey();
        TagBook<ResourceLocation> book = ClientBackpackView.get().tags().toBook();
        return TagPanelModel.of(
                List.of(0), slot -> key, book, hasTool() && registered(key), TagPanelContext::isProtected);
    }

    /** The tag ids the detail page's drop-down offers right now: only while editing, without what the item already has. */
    public static List<String> detailDropdown() {
        TagPanelState state = TagPanelState.get();
        ResourceLocation key = detailKey();
        if (!state.isEditing() || key == null) {
            return List.of();
        }
        List<String> have = ClientBackpackView.get().tags().tagsOf(key);
        return match(TagCandidates.of(PresetTags.ids(), usedTags(), have), state.input().text());
    }

    public static TagPanelLayout detailLayout(int width, int height, TagPanelModel<ResourceLocation> model) {
        TagPanelState state = TagPanelState.get();
        Font font = Minecraft.getInstance().font;
        List<String> labels = new ArrayList<>();
        int pendingIndex = -1;
        for (int i = 0; i < model.commonTags().size(); i++) {
            String tag = model.commonTags().get(i);
            labels.add(TagPanelContext.label(tag));
            if (tag.equals(state.pendingDelete())) {
                pendingIndex = i;
            }
        }
        if (pendingIndex < 0) {
            state.setPendingDelete(null);
        }
        TagPanelLayout layout = TagPanelLayout.of(
                width,
                height,
                font::width,
                labels,
                Component.translatable("tag.mcfloatingbackpack.none").getString(),
                state.isEditing(),
                detailDropdown().size(),
                state.scroll(),
                pendingIndex);
        if (state.scroll() > layout.maxScroll()) {
            state.setScroll(layout.maxScroll());
        }
        return layout;
    }

    /**
     * Back to the list when the item on the detail page has no tags left (the last one was removed, or another change
     * synced in): the overview only manages categories that have tags, so the page cannot stay on an empty one.
     */
    public static void syncDetail() {
        TagOverviewState state = TagOverviewState.get();
        if (state.view() != TagOverviewState.View.DETAIL) {
            return;
        }
        ResourceLocation key = detailKey();
        if (key == null || ClientBackpackView.get().tags().tagsOf(key).isEmpty()) {
            state.backToList();
        }
    }
}
