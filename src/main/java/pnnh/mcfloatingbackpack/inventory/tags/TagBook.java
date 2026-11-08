package pnnh.mcfloatingbackpack.inventory.tags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 018: tags per item category, as an immutable value. Every operation returns a new book; a rejected add changes
 * nothing, so a request that would push any category past {@link TagRules#MAX_TAGS_PER_ITEM} is all-or-nothing.
 * Generic in the key so it can be tested with plain strings; the game uses {@code ResourceLocation}.
 */
public final class TagBook<K> {
    private final Map<K, List<String>> map;

    public TagBook() {
        this.map = Map.of();
    }

    public TagBook(Map<K, List<String>> source) {
        Map<K, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<K, List<String>> entry : source.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        this.map = copy;
    }

    /**
     * Foreign data made safe: invalid and duplicate tags dropped, every category cut to
     * {@link TagRules#MAX_TAGS_PER_ITEM}, empty categories dropped, at most {@code maxCategories} categories kept.
     */
    public static <K> Map<K, List<String>> sanitize(Map<K, List<String>> raw, int maxCategories) {
        Map<K, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<K, List<String>> entry : raw.entrySet()) {
            List<String> kept = new ArrayList<>();
            for (String tag : entry.getValue()) {
                if (TagRules.isValid(tag) && !kept.contains(tag) && kept.size() < TagRules.MAX_TAGS_PER_ITEM) {
                    kept.add(tag);
                }
            }
            if (!kept.isEmpty() && out.size() < maxCategories) {
                out.put(entry.getKey(), kept);
            }
        }
        return out;
    }

    /** A copy of the category's tags (empty if it has none). */
    public List<String> tagsOf(K key) {
        List<String> tags = map.get(key);
        return tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public Map<K, List<String>> asMap() {
        return map;
    }

    /** The tags every given category has, in the first category's order; nothing for an empty selection. */
    public List<String> common(Collection<K> keys) {
        List<String> out = null;
        for (K key : keys) {
            List<String> tags = map.getOrDefault(key, List.of());
            if (out == null) {
                out = new ArrayList<>(tags);
            } else {
                out.retainAll(tags);
            }
        }
        return out == null ? new ArrayList<>() : out;
    }

    /** The categories that already carry the most tags allowed. */
    public List<K> full(Collection<K> keys) {
        List<K> out = new ArrayList<>();
        for (K key : keys) {
            if (map.getOrDefault(key, List.of()).size() >= TagRules.MAX_TAGS_PER_ITEM) {
                out.add(key);
            }
        }
        return out;
    }

    /** False if any category would go over the limit: it lacks the tag and is already full. */
    public boolean canAdd(Collection<K> keys, String tag) {
        for (K key : keys) {
            List<String> tags = map.getOrDefault(key, List.of());
            if (!tags.contains(tag) && tags.size() >= TagRules.MAX_TAGS_PER_ITEM) {
                return false;
            }
        }
        return true;
    }

    /** Adds the tag to every category (skipping those that have it), or returns null and changes nothing. */
    public TagBook<K> addOrNull(Collection<K> keys, String tag) {
        if (!canAdd(keys, tag)) {
            return null;
        }
        Map<K, List<String>> next = new LinkedHashMap<>(map);
        for (K key : keys) {
            List<String> tags = new ArrayList<>(next.getOrDefault(key, List.of()));
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
            next.put(key, tags);
        }
        return new TagBook<>(next);
    }

    /** Like {@link #addOrNull} but returns this book unchanged when the add is refused. */
    public TagBook<K> add(Collection<K> keys, String tag) {
        TagBook<K> next = addOrNull(keys, tag);
        return next == null ? this : next;
    }

    /** Removes the tag from every category that has it. */
    public TagBook<K> remove(Collection<K> keys, String tag) {
        Map<K, List<String>> next = new LinkedHashMap<>(map);
        for (K key : keys) {
            List<String> tags = next.get(key);
            if (tags != null && tags.contains(tag)) {
                List<String> without = new ArrayList<>(tags);
                without.remove(tag);
                if (without.isEmpty()) {
                    next.remove(key);
                } else {
                    next.put(key, without);
                }
            }
        }
        return new TagBook<>(next);
    }

    /** 025: removes every tag of each given category, so the categories disappear from the book; missing ones are ignored. */
    public TagBook<K> clear(Collection<K> keys) {
        Map<K, List<String>> next = new LinkedHashMap<>(map);
        for (K key : keys) {
            next.remove(key);
        }
        return new TagBook<>(next);
    }
}
