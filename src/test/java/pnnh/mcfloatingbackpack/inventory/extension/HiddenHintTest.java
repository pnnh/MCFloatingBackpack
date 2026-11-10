package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.extension.HiddenHint.Line;
import pnnh.mcfloatingbackpack.inventory.extension.RowSet.HiddenSummary;

class HiddenHintTest {
    @Test
    void nothingHiddenSaysNothing() {
        assertTrue(HiddenHint.lines(List.of()).isEmpty());
        assertFalse(HiddenHint.any(List.of()));
    }

    @Test
    void aCategoryWithNoCellHoldingSomethingIsLeftOut() {
        List<HiddenSummary> s = List.of(new HiddenSummary("food", 0), new HiddenSummary("ore", 3));
        assertEquals(List.of(new Line("ore", 3)), HiddenHint.lines(s));
        assertTrue(HiddenHint.any(s));
    }

    @Test
    void onlyEmptyHiddenCategoriesMeanNoDot() {
        assertFalse(HiddenHint.any(List.of(new HiddenSummary("food", 0))));
    }

    @Test
    void theFixedCategoryOrderIsKeptAndUnknownIdsComeLast() {
        List<HiddenSummary> s = List.of(
                new HiddenSummary("zzz", 2), new HiddenSummary("disc", 1), new HiddenSummary("food", 4), new HiddenSummary("aaa", 5));
        assertEquals(
                List.of(new Line("food", 4), new Line("disc", 1), new Line("zzz", 2), new Line("aaa", 5)),
                HiddenHint.lines(s));
    }

    @Test
    void theAnswerMatchesTheRowSetsOwnQuestion() {
        RowSet<String> rows = new RowSet<>(
                List.of(),
                List.of(new Row<>("food", List.of("bread", "", "", "", "", "", "", "", "")), new Row<>("tool", List.of("", "", "", "", "", "", "", "", ""))));
        assertEquals(rows.hasHiddenItems(String::isEmpty), HiddenHint.any(rows.hiddenSummary(String::isEmpty)));
    }
}
