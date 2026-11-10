package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The matcher of the game ORs a data source with the backpack's own tags. Here a fake with the same shape: the data source is
 * a set of item names per category, the manual tags are a map per backpack. What is asked is only ever the placing.
 */
class CategoryMatcherTest {
    private static final Map<String, Set<String>> DATA = Map.of("food", Set.of("bread"), "crop", Set.of("wheat"));

    private static CategoryMatcher<String> matcher(Map<String, List<String>> manualTags) {
        return (category, stack) -> DATA.getOrDefault(category, Set.of()).contains(stack)
                || manualTags.getOrDefault(stack, List.of()).contains(category);
    }

    private static Row<String> rowWith(String category, String item) {
        List<String> cells = new ArrayList<>(java.util.Collections.nCopies(9, ""));
        cells.set(0, item);
        return new Row<>(category, cells);
    }

    @Test
    void eitherSourceIsEnough() {
        CategoryMatcher<String> m = matcher(Map.of("farmers_delight:stew", List.of("food")));
        assertTrue(m.matches("food", "bread"));
        assertTrue(m.matches("food", "farmers_delight:stew"));
        assertFalse(m.matches("food", "cobblestone"));
    }

    @Test
    void aTagOfAnotherCategoryDoesNotMakeAnItemFit() {
        CategoryMatcher<String> m = matcher(Map.of("mod:turnip", List.of("crop")));
        assertTrue(m.matches("crop", "mod:turnip"));
        assertFalse(m.matches("food", "mod:turnip"));
    }

    @Test
    void bothSourcesAtOnceIsTheSameAsOne() {
        CategoryMatcher<String> m = matcher(Map.of("bread", List.of("food")));
        assertTrue(m.matches("food", "bread"));
    }

    @Test
    void eachBackpackHasItsOwnTags() {
        CategoryMatcher<String> a = matcher(Map.of("mod:stew", List.of("food")));
        CategoryMatcher<String> b = matcher(Map.of());
        RowSet<String> rows = new RowSet<>(List.of(rowWith("food", "")), List.of());
        assertTrue(ExtensionRules.canPlace(rows, 4, 4, "mod:stew", a));
        assertFalse(ExtensionRules.canPlace(rows, 4, 4, "mod:stew", b));
    }

    @Test
    void takingATagAwayLeavesWhatIsAlreadyInTheRow() {
        // the row holds the stew; the tag is gone now: the row is untouched, only a new placing would be refused
        RowSet<String> rows = new RowSet<>(List.of(rowWith("food", "mod:stew")), List.of());
        CategoryMatcher<String> without = matcher(Map.of());
        assertEquals("mod:stew", rows.visible().get(0).cells().get(0));
        assertFalse(ExtensionRules.canPlace(rows, 4, 5, "mod:stew", without));
        RowReconciler.Result<String> r = RowReconciler.reconcile(
                rows, Map.of("food", 1), String::isEmpty, () -> "", ExtensionCategories.MAX_ROWS);
        assertEquals("mod:stew", r.rows().visible().get(0).cells().get(0));
    }
}
