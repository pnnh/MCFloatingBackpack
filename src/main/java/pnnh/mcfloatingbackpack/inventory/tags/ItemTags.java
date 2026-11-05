package pnnh.mcfloatingbackpack.inventory.tags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 018: the tags of one backpack, keyed by item registry id (so they belong to the item category, not to any stack).
 * Stored as an optional field of {@code BackpackContents}. Reading drops invalid and duplicate tags and caps every
 * category at {@link TagRules#MAX_TAGS_PER_ITEM}, so foreign data cannot break the limit.
 */
public record ItemTags(Map<ResourceLocation, List<String>> map) {
    /** Most categories one backpack may hold; far above any real use, it only bounds what a stack can grow to. */
    public static final int MAX_CATEGORIES = 1024;

    public static final ItemTags EMPTY = new ItemTags(Map.of());

    public static final Codec<ItemTags> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING.listOf())
            .xmap(ItemTags::sanitize, ItemTags::map);

    public static final StreamCodec<ByteBuf, ItemTags> STREAM_CODEC = ByteBufCodecs.<ByteBuf, ResourceLocation, List<String>, Map<ResourceLocation, List<String>>>map(
                    HashMap::new,
                    ResourceLocation.STREAM_CODEC,
                    ByteBufCodecs.stringUtf8(TagRules.MAX_LENGTH).apply(ByteBufCodecs.list(TagRules.MAX_TAGS_PER_ITEM)),
                    MAX_CATEGORIES)
            .map(ItemTags::new, ItemTags::map);

    public ItemTags {
        map = map == null ? Map.of() : Map.copyOf(map);
    }

    /** The category's tags (a new list; empty if it has none). */
    public List<String> tagsOf(ResourceLocation item) {
        List<String> tags = map.get(item);
        return tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public TagBook<ResourceLocation> toBook() {
        return new TagBook<>(map);
    }

    public static ItemTags of(TagBook<ResourceLocation> book) {
        return new ItemTags(book.asMap());
    }

    private static ItemTags sanitize(Map<ResourceLocation, List<String>> raw) {
        return new ItemTags(TagBook.sanitize(raw, MAX_CATEGORIES));
    }
}
