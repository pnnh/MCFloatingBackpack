package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 065: a backpack is empty for the combined backpack's recipe only if every part is. */
class BackpackEmptinessTest {
    private static final String NONE = "";

    private static BackpackEmptiness.Parts<String> parts(
            List<String> main, String cursor, List<String> left, List<String> right, List<String> bottom, List<String> trash,
            List<List<String>> rows, boolean fluidsEmpty) {
        return new BackpackEmptiness.Parts<>(main, cursor, left, right, bottom, trash, rows, fluidsEmpty);
    }

    private static BackpackEmptiness.Parts<String> allEmpty() {
        return parts(List.of(NONE, NONE), NONE, List.of(NONE), List.of(NONE), List.of(NONE), List.of(), List.of(List.of(NONE, NONE)), true);
    }

    private static boolean empty(BackpackEmptiness.Parts<String> p) {
        return BackpackEmptiness.isEmpty(p, String::isEmpty);
    }

    @Test
    void aBackpackWithNothingInItIsEmpty() {
        assertTrue(empty(allEmpty()));
        assertTrue(empty(parts(List.of(), NONE, List.of(), List.of(), List.of(), List.of(), List.of(), true)));
    }

    @Test
    void anythingInTheMainAreaMakesItNotEmpty() {
        assertFalse(empty(parts(List.of(NONE, "stone"), NONE, List.of(), List.of(), List.of(), List.of(), List.of(), true)));
    }

    @Test
    void theCursorCounts() {
        assertFalse(empty(parts(List.of(NONE), "stone", List.of(), List.of(), List.of(), List.of(), List.of(), true)));
    }

    @Test
    void eachToolAreaCounts() {
        assertFalse(empty(parts(List.of(), NONE, List.of("tool"), List.of(), List.of(), List.of(), List.of(), true)));
        assertFalse(empty(parts(List.of(), NONE, List.of(), List.of("tool"), List.of(), List.of(), List.of(), true)));
        assertFalse(empty(parts(List.of(), NONE, List.of(), List.of(), List.of("tool"), List.of(), List.of(), true)));
    }

    @Test
    void theTrashCanCounts() {
        assertFalse(empty(parts(List.of(), NONE, List.of(), List.of(), List.of(), List.of("junk"), List.of(), true)));
    }

    @Test
    void aCellOfACategoryRowCounts() {
        assertFalse(empty(parts(List.of(), NONE, List.of(), List.of(), List.of(), List.of(), List.of(List.of(NONE, "bread")), true)));
    }

    @Test
    void liquidsCount() {
        assertFalse(empty(parts(List.of(), NONE, List.of(), List.of(), List.of(), List.of(), List.of(), false)));
    }
}
