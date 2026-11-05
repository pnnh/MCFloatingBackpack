package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.item.BackpackTier;

class ToolAreaCapacityTest {
    private static final String EMPTY = "";

    @Test
    void leftAndRightFollowTheTierDefaultRows() {
        for (BackpackTier tier : BackpackTier.values()) {
            assertEquals(tier.rows(), ToolAreaCapacity.of(PanelArea.LEFT, tier.capacity()));
            assertEquals(tier.rows(), ToolAreaCapacity.of(PanelArea.RIGHT, tier.capacity()));
        }
        assertEquals(4, ToolAreaCapacity.of(PanelArea.LEFT, 36));
        assertEquals(12, ToolAreaCapacity.of(PanelArea.RIGHT, 108));
    }

    @Test
    void aCapacityThatIsNotAMultipleOfTheColumnsRoundsUp() {
        assertEquals(5, ToolAreaCapacity.of(PanelArea.LEFT, 37));
        assertEquals(4, ToolAreaCapacity.of(PanelArea.LEFT, 36));
        assertEquals(1, ToolAreaCapacity.of(PanelArea.LEFT, 1));
    }

    @Test
    void theBottomRowSpansTheWholePanelWidth() {
        for (BackpackTier tier : BackpackTier.values()) {
            assertEquals(BackpackTier.COLUMNS + 2, ToolAreaCapacity.of(PanelArea.BOTTOM, tier.capacity()));
        }
        assertEquals(11, ToolAreaCapacity.of(PanelArea.BOTTOM, 36));
    }

    @Test
    void mainIsTheMainCapacity() {
        assertEquals(54, ToolAreaCapacity.of(PanelArea.MAIN, 54));
    }

    @Test
    void fitPadsWithEmptyAndKeepsThePrefixOrder() {
        List<String> fitted = ToolAreaCapacity.fit(List.of("a", "b"), PanelArea.LEFT, 36, EMPTY);
        assertEquals(List.of("a", "b", EMPTY, EMPTY), fitted);
    }

    @Test
    void fitOfNothingGivesAnEmptyFullSizeArea() {
        assertEquals(11, ToolAreaCapacity.fit(null, PanelArea.BOTTOM, 36, EMPTY).size());
        assertEquals(6, ToolAreaCapacity.fit(List.of(), PanelArea.RIGHT, 54, EMPTY).size());
    }

    @Test
    void fitKeepsExactSizeUnchanged() {
        List<String> in = List.of("a", EMPTY, "c", EMPTY);
        assertEquals(in, ToolAreaCapacity.fit(in, PanelArea.LEFT, 36, EMPTY));
    }

    @Test
    void shrinkingNeverDropsAnItem() {
        // 6 slots stored (an iron backpack), the capacity is now 4 (leather): slot 5 still holds something.
        List<String> stored = List.of("a", EMPTY, EMPTY, EMPTY, EMPTY, "z");
        List<String> fitted = ToolAreaCapacity.fit(stored, PanelArea.LEFT, 36, EMPTY);
        assertEquals(stored, fitted);
    }

    @Test
    void shrinkingDropsOnlyEmptyTrailingSlots() {
        List<String> stored = List.of("a", EMPTY, "c", EMPTY, EMPTY, EMPTY);
        assertEquals(List.of("a", EMPTY, "c", EMPTY), ToolAreaCapacity.fit(stored, PanelArea.LEFT, 36, EMPTY));
    }

    @Test
    void theItemCountIsAlwaysPreserved() {
        Random random = new Random(7);
        for (int run = 0; run < 100; run++) {
            int size = random.nextInt(14);
            List<String> stored = new ArrayList<>();
            int items = 0;
            for (int i = 0; i < size; i++) {
                if (random.nextInt(3) == 0) {
                    stored.add("x" + i);
                    items++;
                } else {
                    stored.add(EMPTY);
                }
            }
            int capacity = 9 * (1 + random.nextInt(12));
            PanelArea area = PanelArea.values()[1 + random.nextInt(3)];
            List<String> fitted = ToolAreaCapacity.fit(stored, area, capacity, EMPTY);
            assertEquals(items, fitted.stream().filter(s -> !s.isEmpty()).count());
            assertTrue(fitted.size() >= ToolAreaCapacity.of(area, capacity));
        }
    }

    @Test
    void areaBytesRoundTrip() {
        for (PanelArea area : PanelArea.values()) {
            assertSame(area, PanelArea.fromByte(area.toByte()));
        }
        assertEquals(0, PanelArea.MAIN.toByte());
        assertEquals(3, PanelArea.BOTTOM.toByte());
        assertNull(PanelArea.fromByte((byte) 4));
        assertNull(PanelArea.fromByte((byte) -1));
    }

    @Test
    void onlyTheThreeOuterAreasAreTools() {
        assertFalse(PanelArea.MAIN.isTool());
        assertTrue(PanelArea.LEFT.isTool());
        assertTrue(PanelArea.RIGHT.isTool());
        assertTrue(PanelArea.BOTTOM.isTool());
    }

    @Test
    void thePlacementHookAcceptsOrdinaryItemsInEveryArea() {
        // since 021 only the trash can (right area) and the tag manager (bottom area) are limited, see ToolAreaRulesTest
        for (PanelArea area : PanelArea.values()) {
            assertTrue(ToolAreaRules.canPlace(area, "anything", 0, java.util.List.of("anything"), s -> false, s -> false));
        }
    }
}
