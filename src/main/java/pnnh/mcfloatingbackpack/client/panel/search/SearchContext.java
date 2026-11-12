package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.search.SearchTerms;

/**
 * 042: ties the search to the game: what a cell holds is searched by the item's name in the current language (a renamed item by its
 * new name) and by the path of its registry name ({@code diamond_pickaxe}), both lower-cased. 043: and by the terms other mods'
 * providers give for it (see {@code BackpackSearchApi}), when there are any. Later ways of matching only change {@link #matcherFor}.
 */
@OnlyIn(Dist.CLIENT)
public final class SearchContext {
    private static final SearchState.Source SOURCE = new SearchState.Source() {
        @Override
        public int slotCount() {
            return ClientBackpackView.get().slotCount();
        }

        @Override
        public TextMatcher matcherOf(int slot) {
            ItemStack stack = ClientBackpackView.get().getStackInSlot(slot);
            return stack.isEmpty() ? null : matcherFor(stack);
        }

        @Override
        public boolean hasItem(int slot) {
            return !ClientBackpackView.get().getStackInSlot(slot).isEmpty();
        }
    };

    private SearchContext() {
    }

    /** The backpack the panel shows, as the search state sees it. */
    public static SearchState.Source source() {
        return SOURCE;
    }

    /** The searchable text of one item stack. */
    public static TextMatcher matcherFor(ItemStack stack) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (!SearchTerms.hasActive()) {
            // 043: no provider asked, exactly what 042 did
            return TextMatcher.ofTexts(List.of(name, path));
        }
        List<String> extra = SearchTerms.termsFor(stack);
        if (extra.isEmpty()) {
            return TextMatcher.ofTexts(List.of(name, path));
        }
        List<String> texts = new ArrayList<>(extra.size() + 2);
        texts.add(name);
        texts.add(path);
        texts.addAll(extra);
        return TextMatcher.ofTexts(texts);
    }

    /** Whether the item in a tool area's cell matches the query (the tool areas are highlighted, never filtered). */
    public static boolean toolMatches(SearchQuery query, PanelArea area, int index) {
        if (query.isEmpty()) {
            return false;
        }
        ItemStack stack = ClientBackpackView.get().getToolStack(area, index);
        return !stack.isEmpty() && SearchMatcher.matches(query, matcherFor(stack));
    }
}
