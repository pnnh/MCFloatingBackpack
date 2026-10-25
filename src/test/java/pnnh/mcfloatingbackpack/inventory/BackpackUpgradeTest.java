package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class BackpackUpgradeTest {
    @Test
    void prefixSurvivesLargerCapacity() {
        List<String> grown = BackpackUpgrade.fit(List.of("iron", "bread", "oak", "string", "apple"), 8, "");
        assertEquals(List.of("iron", "bread", "oak", "string", "apple", "", "", ""), grown);
    }

    @Test
    void longerListIsTruncated() {
        List<String> shrunk = BackpackUpgrade.fit(List.of("a", "b", "c"), 2, "");
        assertEquals(List.of("a", "b"), shrunk);
    }

    @Test
    void nullSourceBecomesEmptySlots() {
        assertEquals(List.of("", ""), BackpackUpgrade.fit(null, 2, ""));
    }
}
