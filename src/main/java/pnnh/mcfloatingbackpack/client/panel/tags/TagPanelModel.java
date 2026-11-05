package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import pnnh.mcfloatingbackpack.inventory.tags.TagBook;
import pnnh.mcfloatingbackpack.inventory.trash.TagProtection;

/**
 * 018: what the tag panel shows for the current selection. The selected slots are turned into item categories (the same
 * item in several slots counts once, empty slots are ignored), and the tags shown are the ones every category has.
 * Generic in the category type so it can be tested with strings; the game uses item registry ids.
 */
public final class TagPanelModel<K> {
    public enum Status {
        /** No slot with an item is selected. */
        NO_ITEMS,
        ITEMS
    }

    private final Status status;
    private final List<K> categories;
    private final List<String> commonTags;
    private final List<K> fullItems;
    private final List<K> protectedItems;
    private final boolean canAdd;

    private TagPanelModel(
            Status status,
            List<K> categories,
            List<String> commonTags,
            List<K> fullItems,
            List<K> protectedItems,
            boolean canAdd) {
        this.status = status;
        this.categories = categories;
        this.commonTags = commonTags;
        this.fullItems = fullItems;
        this.protectedItems = protectedItems;
        this.canAdd = canAdd;
    }

    /**
     * @param selected the selected main-area slot indices
     * @param categoryAt the category of the item in a slot, or null for an empty slot
     * @param hasTool whether a tag manager is in the function area (adding needs it)
     */
    public static <K> TagPanelModel<K> of(
            Collection<Integer> selected, IntFunction<K> categoryAt, TagBook<K> book, boolean hasTool) {
        return of(selected, categoryAt, book, hasTool, category -> false);
    }

    /**
     * @param isProtected whether a category is on the protection list (022): such a category may not get the trash tag
     */
    public static <K> TagPanelModel<K> of(
            Collection<Integer> selected,
            IntFunction<K> categoryAt,
            TagBook<K> book,
            boolean hasTool,
            Predicate<K> isProtected) {
        List<K> categories = new ArrayList<>();
        for (int slot : selected) {
            K category = categoryAt.apply(slot);
            if (category != null && !categories.contains(category)) {
                categories.add(category);
            }
        }
        if (categories.isEmpty()) {
            return new TagPanelModel<>(Status.NO_ITEMS, categories, List.of(), List.of(), List.of(), false);
        }
        List<K> full = book.full(categories);
        List<K> guarded = categories.stream().filter(isProtected).toList();
        return new TagPanelModel<>(Status.ITEMS, categories, book.common(categories), full, guarded, hasTool && full.isEmpty());
    }

    public Status status() {
        return status;
    }

    /** The distinct item categories selected, in order of first appearance. */
    public List<K> categories() {
        return categories;
    }

    /** The tags every selected category has (a single category: all its tags). */
    public List<String> commonTags() {
        return commonTags;
    }

    /** The selected categories that already carry the most tags allowed. */
    public List<K> fullItems() {
        return fullItems;
    }

    /** The selected categories on the protection list (022), in selection order. */
    public List<K> protectedItems() {
        return protectedItems;
    }

    /** Whether adding this tag to the selection is refused because a protected item is in it: only the trash tag is. */
    public boolean blocks(String tag) {
        return TagProtection.blocks(tag, protectedItems.size());
    }

    public boolean canAdd() {
        return canAdd;
    }
}
