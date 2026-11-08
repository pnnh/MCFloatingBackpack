package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewModel.Row;
import pnnh.mcfloatingbackpack.inventory.tags.TagBook;

/** 025: the rows of the overview list: which categories, in what order, under which filter. */
class TagOverviewModelTest {
    // key -> display name; a key that is not in the map is "no longer in the game" and is shown by its id
    private static final Map<String, String> NAMES = Map.of("m:log", "Oak Log", "m:stone", "stone", "m:dirt", "Dirt");

    private static String nameOf(String key) {
        return NAMES.getOrDefault(key, key);
    }

    private static TagBook<String> book() {
        TagBook<String> b = new TagBook<>();
        b = b.add(List.of("m:log"), "wood").add(List.of("m:log"), "trash");
        b = b.add(List.of("m:stone"), "stone");
        b = b.add(List.of("m:dirt"), "trash");
        b = b.add(List.of("gone:thing"), "keep");
        return b;
    }

    private static List<String> keys(List<Row<String>> rows) {
        return rows.stream().map(Row::key).toList();
    }

    @Test
    void listsEveryCategoryThatHasTagsSortedByNameIgnoringCase() {
        List<Row<String>> rows = TagOverviewModel.rows(book(), TagOverviewModelTest::nameOf, k -> k, null);
        // Dirt, gone:thing, Oak Log, stone
        assertEquals(List.of("m:dirt", "gone:thing", "m:log", "m:stone"), keys(rows));
    }

    @Test
    void sameNamesAreOrderedById() {
        TagBook<String> b = new TagBook<String>().add(List.of("b:x"), "t").add(List.of("a:x"), "t");
        List<Row<String>> rows = TagOverviewModel.rows(b, k -> "Same", k -> k, null);
        assertEquals(List.of("a:x", "b:x"), keys(rows));
    }

    @Test
    void aRowCarriesItsTags() {
        List<Row<String>> rows = TagOverviewModel.rows(book(), TagOverviewModelTest::nameOf, k -> k, null);
        Row<String> log = rows.stream().filter(r -> r.key().equals("m:log")).findFirst().orElseThrow();
        assertEquals(List.of("wood", "trash"), log.tags());
        assertEquals("Oak Log", log.name());
    }

    @Test
    void aFilterKeepsOnlyCategoriesWithThatTag() {
        List<Row<String>> rows = TagOverviewModel.rows(book(), TagOverviewModelTest::nameOf, k -> k, "trash");
        assertEquals(List.of("m:dirt", "m:log"), keys(rows));
    }

    @Test
    void anEmptyOrMissingFilterMeansNoFilter() {
        assertEquals(4, TagOverviewModel.rows(book(), TagOverviewModelTest::nameOf, k -> k, "").size());
        assertEquals(4, TagOverviewModel.rows(book(), TagOverviewModelTest::nameOf, k -> k, null).size());
    }

    @Test
    void aFilterNobodyHasGivesNoRows() {
        assertTrue(TagOverviewModel.rows(book(), TagOverviewModelTest::nameOf, k -> k, "nope").isEmpty());
    }

    @Test
    void anEmptyBookGivesNoRows() {
        assertTrue(TagOverviewModel.rows(new TagBook<String>(), TagOverviewModelTest::nameOf, k -> k, null).isEmpty());
    }
}
