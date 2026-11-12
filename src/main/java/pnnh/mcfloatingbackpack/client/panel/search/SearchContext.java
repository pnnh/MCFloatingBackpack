package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.extension.CategoryNames;
import pnnh.mcfloatingbackpack.inventory.extension.HiddenHint;
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

        @Override
        public List<HiddenSummaryRows.Line> hiddenLines() {
            // 037's own numbers and names: the categories with something hidden, in the fixed order; a category this version does not
            // know (made by a newer one) has no language entry and is shown by its id
            List<HiddenSummaryRows.Line> lines = new ArrayList<>();
            for (HiddenHint.Line line : HiddenHint.lines(ClientBackpackView.get().rowView().hidden())) {
                String name = Component.translatableWithFallback(CategoryNames.langKey(line.category()), line.category()).getString();
                lines.add(new HiddenSummaryRows.Line(line.category(), name, line.cells()));
            }
            return lines;
        }
    };

    private SearchContext() {
    }

    /** The backpack the panel shows, as the search state sees it. */
    public static SearchState.Source source() {
        return SOURCE;
    }

    /** The searchable text of one item stack; 044: and what {@code @mod} and {@code #tag} ask about it. */
    public static TextMatcher matcherFor(ItemStack stack) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        TextMatcher words;
        if (!SearchTerms.hasActive()) {
            // 043: no provider asked, exactly what 042 did
            words = TextMatcher.ofTexts(List.of(name, path));
        } else {
            List<String> extra = SearchTerms.termsFor(stack);
            if (extra.isEmpty()) {
                words = TextMatcher.ofTexts(List.of(name, path));
            } else {
                List<String> texts = new ArrayList<>(extra.size() + 2);
                texts.add(name);
                texts.add(path);
                texts.addAll(extra);
                words = TextMatcher.ofTexts(texts);
            }
        }
        Item item = stack.getItem();
        // 044: mods and tags are looked up only when a query asks (and then from a cache per item kind, see ItemFacts); the providers'
        // words are in the texts above and are not asked about mods or tags
        return new TextMatcher() {
            @Override
            public boolean contains(String needleLowerCase) {
                return words.contains(needleLowerCase);
            }

            @Override
            public boolean inMod(String needleLowerCase) {
                return ItemFacts.inMod(item, needleLowerCase);
            }

            @Override
            public boolean hasTag(String needleLowerCase) {
                return ItemFacts.hasTag(item, needleLowerCase);
            }
        };
    }

    /** Whether the item in a tool area's cell matches the query (the tool areas are highlighted, never filtered). */
    public static boolean toolMatches(SearchQuery query, PanelArea area, int index) {
        if (query.isEmpty() || query.command().type() != SearchQuery.Command.Type.NONE) {
            // 044: a command (/hidden, or one that does not exist) frames nothing in the tool areas
            return false;
        }
        ItemStack stack = ClientBackpackView.get().getToolStack(area, index);
        return !stack.isEmpty() && SearchMatcher.matches(query, matcherFor(stack));
    }
}
