package pnnh.mcfloatingbackpack.inventory.quick;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.item.BackpackTier;

class QuickAccessPlaceTest {
    @Test
    void aDifferentTierIsRefusedEvenWhenItReplacesTheSameSlot() {
        assertEquals(
                QuickAccessPlace.Verdict.WRONG_TIER,
                QuickAccessPlace.check(true, BackpackTier.LEATHER, BackpackTier.IRON, 0, List.of()));
        assertEquals(
                QuickAccessPlace.Verdict.WRONG_TIER,
                QuickAccessPlace.check(true, BackpackTier.IRON, BackpackTier.LEATHER, 0, List.of(BackpackTier.LEATHER)));
    }

    @Test
    void theSameSlotMayBeReplacedWithTheMatchingTier() {
        assertEquals(
                QuickAccessPlace.Verdict.OK,
                QuickAccessPlace.check(true, BackpackTier.IRON, BackpackTier.IRON, 0, List.of(BackpackTier.LEATHER)));
        assertEquals(
                QuickAccessPlace.Verdict.OK,
                QuickAccessPlace.check(true, BackpackTier.NETHERITE, BackpackTier.NETHERITE, 1, Arrays.asList(null, BackpackTier.NETHERITE)));
    }

    @Test
    void aSecondQuickAccessInAnotherSlotIsRefusedWhateverItsTier() {
        assertEquals(
                QuickAccessPlace.Verdict.DUPLICATE,
                QuickAccessPlace.check(true, BackpackTier.IRON, BackpackTier.IRON, 1, Arrays.asList(BackpackTier.IRON, null)));
        assertEquals(
                QuickAccessPlace.Verdict.DUPLICATE,
                QuickAccessPlace.check(true, BackpackTier.IRON, BackpackTier.IRON, 1, Arrays.asList(BackpackTier.LEATHER, null)));
    }

    @Test
    void otherAreasAndOtherItemsAreNotLimited() {
        assertEquals(
                QuickAccessPlace.Verdict.OK,
                QuickAccessPlace.check(false, BackpackTier.LEATHER, BackpackTier.NETHERITE, 0, List.of(BackpackTier.NETHERITE)));
        assertEquals(
                QuickAccessPlace.Verdict.OK,
                QuickAccessPlace.check(true, BackpackTier.LEATHER, null, 0, List.of(BackpackTier.LEATHER)));
    }

    @Test
    void aBackpackWithoutATierTakesNoQuickAccess() {
        assertEquals(
                QuickAccessPlace.Verdict.WRONG_TIER,
                QuickAccessPlace.check(true, null, BackpackTier.LEATHER, 0, List.of()));
    }
}
