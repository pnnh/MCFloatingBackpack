package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelContext;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;

/**
 * 044: what {@code @mod} and {@code #tag} ask about an item: its namespace, its mod's display name, its item tags and the backpack's
 * tags on it. The search asks every frame (the tool areas' frames) and every key (400 cells), so everything is cached per item kind:
 * the namespace, mod name and item tags until the tags are sent again or the resources reload, the backpack's tags until the backpack's
 * tag book is a different object (it is replaced whole whenever it changes) or the resources reload (the names of the presets are in
 * the current language). Only the render thread uses it; other threads only call {@link #invalidate()}, which is a counter.
 */
@OnlyIn(Dist.CLIENT)
public final class ItemFacts {
    private record Facts(String namespace, String modNameLowerCase, List<String> tagIdsLowerCase) {
    }

    private static final AtomicInteger INVALIDATIONS = new AtomicInteger();
    private static final Map<Item, Facts> FACTS = new HashMap<>();
    private static final Map<Item, List<TagMatch.Entry>> BACKPACK_TAGS = new HashMap<>();
    private static int seenInvalidations;
    private static ItemTags backpackTagBook;

    private ItemFacts() {
    }

    /** The tags were sent again, or the resources reloaded: forget what was worked out. Safe from any thread. */
    public static void invalidate() {
        INVALIDATIONS.incrementAndGet();
    }

    private static void checkInvalidated() {
        int now = INVALIDATIONS.get();
        if (now != seenInvalidations) {
            seenInvalidations = now;
            FACTS.clear();
            BACKPACK_TAGS.clear();
            backpackTagBook = null;
        }
    }

    private static Facts factsOf(Item item) {
        checkInvalidated();
        return FACTS.computeIfAbsent(item, ItemFacts::work);
    }

    private static Facts work(Item item) {
        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace().toLowerCase(Locale.ROOT);
        String modName = ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse("")
                .toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        BuiltInRegistries.ITEM.wrapAsHolder(item).tags().forEach(key -> tags.add(key.location().toString().toLowerCase(Locale.ROOT)));
        return new Facts(namespace, modName, List.copyOf(tags));
    }

    private static List<TagMatch.Entry> backpackTagsOf(Item item) {
        checkInvalidated();
        ItemTags book = ClientBackpackView.get().tags();
        if (book != backpackTagBook) {
            backpackTagBook = book;
            BACKPACK_TAGS.clear();
        }
        return BACKPACK_TAGS.computeIfAbsent(item, kind -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(kind);
            List<TagMatch.Entry> entries = new ArrayList<>();
            for (String tag : book.tagsOf(id)) {
                entries.add(new TagMatch.Entry(tag.toLowerCase(Locale.ROOT), TagPanelContext.label(tag).toLowerCase(Locale.ROOT)));
            }
            return List.copyOf(entries);
        });
    }

    /** {@code @needle}: the item's mod. */
    public static boolean inMod(Item item, String needleLowerCase) {
        Facts facts = factsOf(item);
        return ModMatch.matches(needleLowerCase, facts.namespace(), facts.modNameLowerCase());
    }

    /** {@code #needle}: the item's tags or the backpack's tags on it. */
    public static boolean hasTag(Item item, String needleLowerCase) {
        return TagMatch.itemTag(needleLowerCase, factsOf(item).tagIdsLowerCase())
                || TagMatch.backpackTag(needleLowerCase, backpackTagsOf(item));
    }
}
