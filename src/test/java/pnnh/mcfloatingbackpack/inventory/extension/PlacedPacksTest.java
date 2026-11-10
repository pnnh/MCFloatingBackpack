package pnnh.mcfloatingbackpack.inventory.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.extension.PlacedPacks.Pack;
import pnnh.mcfloatingbackpack.inventory.extension.PlacedPacks.Place;

class PlacedPacksTest {
    private static Pack category(String category, Place place) {
        return new Pack(category, null, place);
    }

    private static Pack universal(String tier, Place place) {
        return new Pack("general", tier, place);
    }

    private static int general(List<Pack> packs, String backpackTier) {
        return PlacedPacks.count(packs, backpackTier).getOrDefault("general", 0);
    }

    @Test
    void categoryExtensionsCountWhereverTheyAre() {
        Map<String, Integer> placed = PlacedPacks.count(
                List.of(
                        category("food", Place.LEFT),
                        category("food", Place.BOTTOM),
                        category("food", Place.CURSOR),
                        category("tool", Place.RIGHT)),
                "leather");
        assertEquals(Map.of("food", 3, "tool", 1), placed);
    }

    @Test
    void noPacksCountNothing() {
        assertEquals(Map.of(), PlacedPacks.count(List.of(), "leather"));
    }

    @Test
    void oneInTheLeftAreaCountsOne() {
        assertEquals(1, general(List.of(universal("leather", Place.LEFT)), "leather"));
    }

    @Test
    void oneInEachSideAreaCountsTwo() {
        assertEquals(2, general(List.of(universal("leather", Place.LEFT), universal("leather", Place.RIGHT)), "leather"));
    }

    @Test
    void severalInTheSameAreaCountOnce() {
        assertEquals(1, general(List.of(universal("iron", Place.LEFT), universal("iron", Place.LEFT), universal("iron", Place.LEFT)), "iron"));
    }

    @Test
    void theBottomAreaDoesNotCount() {
        assertEquals(0, general(List.of(universal("gold", Place.BOTTOM)), "gold"));
        assertEquals(1, general(List.of(universal("gold", Place.BOTTOM), universal("gold", Place.RIGHT)), "gold"));
    }

    @Test
    void aPackOfAnotherTierDoesNotCount() {
        assertEquals(0, general(List.of(universal("iron", Place.LEFT)), "leather"));
        assertEquals(0, general(List.of(universal("netherite", Place.LEFT), universal("netherite", Place.RIGHT)), "diamond"));
    }

    @Test
    void anAreaWithAWrongTierAndARightOneCountsOnceWhateverTheOrder() {
        List<Pack> a = List.of(universal("iron", Place.LEFT), universal("leather", Place.LEFT));
        List<Pack> b = List.of(universal("leather", Place.LEFT), universal("iron", Place.LEFT));
        assertEquals(1, general(a, "leather"));
        assertEquals(1, general(b, "leather"));
    }

    @Test
    void theCursorCountsButTheTotalStaysAtTwo() {
        assertEquals(1, general(List.of(universal("leather", Place.CURSOR)), "leather"));
        assertEquals(2, general(List.of(universal("leather", Place.LEFT), universal("leather", Place.CURSOR)), "leather"));
        assertEquals(2, general(
                List.of(universal("leather", Place.LEFT), universal("leather", Place.RIGHT), universal("leather", Place.CURSOR)), "leather"));
        assertEquals(0, general(List.of(universal("iron", Place.CURSOR)), "leather"));
    }

    @Test
    void anUnknownBackpackTierCountsNothing() {
        assertEquals(0, general(List.of(universal("leather", Place.LEFT)), null));
    }

    @Test
    void thereIsNoGeneralEntryWhenNothingCounts() {
        assertFalse(PlacedPacks.count(List.of(universal("iron", Place.LEFT)), "leather").containsKey("general"));
    }

    @Test
    void universalExtensionsDoNotChangeTheCategoryCounts() {
        Map<String, Integer> placed = PlacedPacks.count(
                List.of(category("ore", Place.LEFT), universal("leather", Place.LEFT), universal("leather", Place.RIGHT)), "leather");
        assertEquals(Map.of("ore", 1, "general", 2), placed);
    }

    @Test
    void theAnswerDoesNotDependOnTheOrderOfThePacks() {
        List<Pack> packs = new ArrayList<>(List.of(
                universal("leather", Place.LEFT), universal("leather", Place.LEFT), universal("iron", Place.RIGHT),
                universal("leather", Place.CURSOR), category("food", Place.BOTTOM), category("food", Place.LEFT),
                universal("leather", Place.BOTTOM)));
        Map<String, Integer> expected = PlacedPacks.count(packs, "leather");
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            Collections.shuffle(packs, random);
            assertEquals(expected, PlacedPacks.count(packs, "leather"));
        }
    }
}
