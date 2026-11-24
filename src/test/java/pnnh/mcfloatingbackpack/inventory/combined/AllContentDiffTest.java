package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AllContentDiffTest {
    @Test
    void theSameObjectIsNotEvenCompared() {
        assertFalse(AllContentDiff.changed(true, () -> {
            throw new AssertionError("compared");
        }, () -> false, () -> false));
    }

    @Test
    void aNewObjectWithTheSameThingsIsNotAChange() {
        assertFalse(AllContentDiff.changed(false, () -> true, () -> true, () -> true));
    }

    @Test
    void anyDifferenceShownToTheClientIsAChange() {
        assertTrue(AllContentDiff.changed(false, () -> false, () -> true, () -> true));
        assertTrue(AllContentDiff.changed(false, () -> true, () -> false, () -> true));
        assertTrue(AllContentDiff.changed(false, () -> true, () -> true, () -> false));
    }

    @Test
    void listsAreComparedOneByOne() {
        assertTrue(AllContentDiff.listsMatch(List.of(1, 2, 3), List.of(1, 2, 3), Integer::equals));
        assertFalse(AllContentDiff.listsMatch(List.of(1, 2, 3), List.of(1, 2, 4), Integer::equals));
        assertFalse(AllContentDiff.listsMatch(List.of(1, 2), List.of(1, 2, 3), Integer::equals));
        assertTrue(AllContentDiff.listsMatch(List.<Integer>of(), List.<Integer>of(), Integer::equals));
    }
}
