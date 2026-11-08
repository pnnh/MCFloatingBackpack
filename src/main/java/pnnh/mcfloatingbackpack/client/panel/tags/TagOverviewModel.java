package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import pnnh.mcfloatingbackpack.inventory.tags.TagBook;

/**
 * 025: the rows of the overview list: every item category the backpack has tags for (whether or not the item is in the
 * backpack), optionally only those carrying one tag, sorted by display name (ignoring case) and then by id. Generic in
 * the category key so it can be tested with strings; the game uses item registry ids. Pure logic.
 */
public final class TagOverviewModel {
    /** One category: its key, the name shown for it, and its tags in the order they were added. */
    public record Row<K>(K key, String name, List<String> tags) {
    }

    private TagOverviewModel() {
    }

    /** @param filter a tag every row must carry, or null/empty for no filter */
    public static <K> List<Row<K>> rows(
            TagBook<K> book, Function<K, String> nameOf, Function<K, String> idOf, String filter) {
        boolean filtering = filter != null && !filter.isEmpty();
        List<Row<K>> rows = new ArrayList<>();
        for (Map.Entry<K, List<String>> entry : book.asMap().entrySet()) {
            List<String> tags = entry.getValue();
            if (tags.isEmpty() || (filtering && !tags.contains(filter))) {
                continue;
            }
            rows.add(new Row<>(entry.getKey(), nameOf.apply(entry.getKey()), List.copyOf(tags)));
        }
        rows.sort(Comparator.<Row<K>, String>comparing(Row::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(row -> idOf.apply(row.key())));
        return rows;
    }
}
