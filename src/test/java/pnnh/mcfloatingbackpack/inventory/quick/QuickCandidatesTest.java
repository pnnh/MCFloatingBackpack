package pnnh.mcfloatingbackpack.inventory.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class QuickCandidatesTest {
    private static final Set<String> QUICK = Set.of("minecraft:arrow", "minecraft:cooked_beef");

    private static boolean tagged(String id) {
        return QUICK.contains(id);
    }

    @Test
    void anExtensionRowIsNineCellsWide() {
        assertEquals(9, QuickCandidates.ROW_CELLS);
        assertEquals("quick", QuickCandidates.TAG);
    }

    @Test
    void blanksAndUntaggedStacksAreSkippedAndOrderIsKept() {
        List<String> slots = new ArrayList<>(List.of(
                "",
                "minecraft:dirt",
                "minecraft:arrow",
                "minecraft:cooked_beef",
                "minecraft:arrow"));
        slots.add(3, null);
        assertEquals(List.of(2, 4, 5), QuickCandidates.select(36, slots, QuickCandidatesTest::tagged));
    }

    @Test
    void theSameItemIdCanAppearMoreThanOnce() {
        List<String> slots = List.of("minecraft:arrow", "minecraft:arrow", "minecraft:stick", "minecraft:arrow");
        assertEquals(List.of(0, 1, 3), QuickCandidates.select(36, slots, QuickCandidatesTest::tagged));
    }

    @Test
    void eachTierKeepsAtMostHalfItsOwnCapacity() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            many.add("minecraft:arrow");
        }
        assertEquals(18, QuickCandidates.select(36, many, QuickCandidatesTest::tagged).size());
        assertEquals(27, QuickCandidates.select(54, many, QuickCandidatesTest::tagged).size());
        assertEquals(36, QuickCandidates.select(72, many, QuickCandidatesTest::tagged).size());
        assertEquals(45, QuickCandidates.select(90, many, QuickCandidatesTest::tagged).size());
        assertEquals(54, QuickCandidates.select(108, many, QuickCandidatesTest::tagged).size());
        assertEquals(List.of(0), List.of(QuickCandidates.select(36, many, QuickCandidatesTest::tagged).get(0)));
        assertEquals(17, QuickCandidates.select(36, many, QuickCandidatesTest::tagged).get(17));
    }

    @Test
    void visibleExtensionCellsKeepTheirSlotIndexAndTheCapStillComesFromTheTier() {
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            slots.add("");
        }
        // one visible row of 9, not a hidden row (the caller never passes those)
        for (int i = 0; i < QuickCandidates.ROW_CELLS; i++) {
            slots.add(i % 2 == 0 ? "minecraft:arrow" : "minecraft:dirt");
        }
        assertEquals(List.of(36, 38, 40, 42, 44), QuickCandidates.select(36, slots, QuickCandidatesTest::tagged));
    }

    @Test
    void nothingToChooseFromStaysEmpty() {
        assertTrue(QuickCandidates.select(36, null, QuickCandidatesTest::tagged).isEmpty());
        assertTrue(QuickCandidates.select(36, List.of("minecraft:arrow"), null).isEmpty());
        assertTrue(QuickCandidates.select(0, List.of("minecraft:arrow"), QuickCandidatesTest::tagged).isEmpty());
        assertTrue(QuickCandidates.select(-4, List.of("minecraft:arrow"), QuickCandidatesTest::tagged).isEmpty());
        List<String> blanks = new ArrayList<>();
        blanks.add("");
        blanks.add(null);
        blanks.add("minecraft:dirt");
        assertTrue(QuickCandidates.select(36, blanks, QuickCandidatesTest::tagged).isEmpty());
    }
}
