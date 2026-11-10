package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 037: the overloads that take the categories of the visible rows alone give the same answers as the ones that take a RowSet. */
class ExtensionRulesCategoriesTest {
    private static final CategoryMatcher<String> MATCHER = (category, stack) ->
            !stack.isEmpty() && (stack.equals("trident") ? category.equals("tool") || category.equals("weapon") : stack.startsWith(category + ":"));

    private static RowSet<String> set(List<String> categories) {
        List<Row<String>> visible = new ArrayList<>();
        for (String category : categories) {
            visible.add(new Row<>(category, List.of("", "", "", "", "", "", "", "", "")));
        }
        return new RowSet<>(visible, List.of());
    }

    private static final List<List<String>> CASES = List.of(
            List.of(),
            List.of("food"),
            List.of("tool", "weapon"),
            List.of("weapon", "tool", "weapon"),
            List.of("food", "food", "ore", "disc"));

    private static final String[] STACKS = {"", "food:bread", "tool:axe", "weapon:sword", "ore:iron", "disc:cat", "trident", "cobblestone"};

    @Test
    void canPlaceAgreesForEverySlotAndStack() {
        for (List<String> categories : CASES) {
            RowSet<String> rows = set(categories);
            for (int base : new int[] {0, 9, 36, 108}) {
                for (int slot = -2; slot < base + 9 * categories.size() + 12; slot++) {
                    for (String stack : STACKS) {
                        assertEquals(
                                ExtensionRules.canPlace(rows, base, slot, stack, MATCHER),
                                ExtensionRules.canPlace(categories, base, slot, stack, MATCHER),
                                categories + " base " + base + " slot " + slot + " " + stack);
                    }
                }
            }
        }
    }

    @Test
    void targetSegmentsAgree() {
        for (List<String> categories : CASES) {
            RowSet<String> rows = set(categories);
            for (int base : new int[] {0, 27, 36}) {
                for (String stack : STACKS) {
                    List<int[]> a = ExtensionRules.targetSegments(rows, base, stack, MATCHER);
                    List<int[]> b = ExtensionRules.targetSegments(categories, base, stack, MATCHER);
                    assertEquals(a.size(), b.size());
                    for (int i = 0; i < a.size(); i++) {
                        assertArrayEquals(a.get(i), b.get(i), categories + " base " + base + " " + stack);
                    }
                }
            }
        }
    }

    @Test
    void aPlacementIntoAHiddenOrMissingRowIsRefused() {
        // Only the visible rows are in the list, so a slot past them is in no row.
        assertEquals(false, ExtensionRules.canPlace(List.of("food"), 36, 45, "food:bread", MATCHER));
    }
}
