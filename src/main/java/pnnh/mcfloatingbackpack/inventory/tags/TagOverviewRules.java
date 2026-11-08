package pnnh.mcfloatingbackpack.inventory.tags;

import java.util.List;

import pnnh.mcfloatingbackpack.inventory.tags.TagOverviewRequest.Op;
import pnnh.mcfloatingbackpack.inventory.trash.TagProtection;

/**
 * 025: what a request from the tag overview does to the backpack's tag book. Generic in the category key so it can be
 * tested with plain strings; the game uses item registry ids. The overview only manages categories that already carry
 * tags, so:
 *
 * <ul>
 *   <li>ADD is refused for a category without tags, for an item no longer in the game ({@code registered} false: whether it
 *       is protected cannot be told), for the trash tag on a protected item (022), and beyond
 *       {@link TagRules#MAX_TAGS_PER_ITEM}; adding a tag the category already has changes nothing.
 *   <li>REMOVE and CLEAR never fail: a missing category or tag changes nothing; the last tag removed makes the category
 *       disappear. They work for protected items and for items no longer in the game.
 * </ul>
 *
 * The result is {@code null} when the request is refused, otherwise the new book (equal to the old one when nothing
 * changed). The given book is never changed. Pure logic.
 */
public final class TagOverviewRules {
    private TagOverviewRules() {
    }

    public static <K> TagBook<K> apply(
            TagBook<K> book, Op op, K key, String tag, boolean registered, boolean isProtected) {
        List<K> keys = List.of(key);
        switch (op) {
            case ADD -> {
                if (book.tagsOf(key).isEmpty() || !registered || TagProtection.blocks(tag, isProtected ? 1 : 0)) {
                    return null;
                }
                if (book.tagsOf(key).contains(tag)) {
                    return book;
                }
                return book.addOrNull(keys, tag);
            }
            case REMOVE -> {
                return book.remove(keys, tag);
            }
            case CLEAR -> {
                return book.clear(keys);
            }
            default -> {
                return null;
            }
        }
    }
}
