package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.OrphanRules.Entry;

/** 062: which backpacks of a vanished Accessories slot type are given back, and never one too many. */
class OrphanRulesTest {
    private static final String IRON = "mcfloatingbackpack:iron_floating_backpack";
    private static final String GOLD = "mcfloatingbackpack:gold_floating_backpack";
    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final Set<String> TYPES = Set.of("back", "charm");
    private static final Map<String, Integer> NONE = Map.of();

    private static List<Integer> decide(List<Entry> entries, Set<UUID> present, Map<String, Integer> file, Map<String, Integer> live) {
        return OrphanRules.decide(entries, TYPES, present, file, live);
    }

    @Test
    void aNumberedBackpackOfAVanishedSlotIsGivenBack() {
        List<Entry> entries = List.of(new Entry("floating_backpack", IRON, A));
        assertEquals(List.of(0), decide(entries, Set.of(), NONE, NONE));
    }

    @Test
    void aNumberedBackpackThePlayerHasAlreadyIsNotGivenBackAgain() {
        List<Entry> entries = List.of(new Entry("floating_backpack", IRON, A));
        assertTrue(decide(entries, Set.of(A), NONE, NONE).isEmpty());
        assertEquals(List.of(0), decide(entries, Set.of(B), NONE, NONE), "another number does not count");
    }

    @Test
    void aBackpackInASlotTypeThatStillExistsIsNeverTouched() {
        List<Entry> entries = List.of(new Entry("back", IRON, A), new Entry("charm", GOLD, null));
        assertTrue(decide(entries, Set.of(), Map.of(GOLD, 1), NONE).isEmpty());
    }

    @Test
    void anUnnumberedBackpackIsGivenBackWhenOneIsMissing() {
        List<Entry> entries = List.of(new Entry("floating_backpack", IRON, null));
        assertEquals(List.of(0), decide(entries, Set.of(), Map.of(IRON, 1), Map.of(IRON, 0)));
    }

    @Test
    void anUnnumberedBackpackIsNotGivenBackWhenThePlayerHasAsManyAsBefore() {
        List<Entry> entries = List.of(new Entry("floating_backpack", IRON, null));
        assertTrue(decide(entries, Set.of(), Map.of(IRON, 1), Map.of(IRON, 1)).isEmpty(), "something else gave it back");
        assertTrue(decide(entries, Set.of(), Map.of(IRON, 1), Map.of(IRON, 3)).isEmpty());
    }

    @Test
    void asManyAreGivenBackAsAreMissingAndNoMoreThanLayInTheVanishedSlots() {
        List<Entry> entries = List.of(
                new Entry("floating_backpack", IRON, null), new Entry("floating_backpack", IRON, null), new Entry("back", IRON, null));
        // the saved data showed three, the player has one now: two are missing, and two lay in the vanished slot
        assertEquals(List.of(0, 1), decide(entries, Set.of(), Map.of(IRON, 3), Map.of(IRON, 1)));
        // only one is missing
        assertEquals(List.of(0), decide(entries, Set.of(), Map.of(IRON, 3), Map.of(IRON, 2)));
        // more missing than lay in the vanished slots (the rest lay in a slot that still exists): never more than that
        assertEquals(List.of(0, 1), decide(entries, Set.of(), Map.of(IRON, 9), Map.of(IRON, 0)));
    }

    @Test
    void theItemsAreCountedEachOnItsOwn() {
        List<Entry> entries = List.of(new Entry("floating_backpack", IRON, null), new Entry("floating_backpack", GOLD, null));
        assertEquals(List.of(1), decide(entries, Set.of(), Map.of(IRON, 1, GOLD, 1), Map.of(IRON, 1)));
    }

    @Test
    void numberedAndUnnumberedAreDecidedSeparately() {
        List<Entry> entries = List.of(
                new Entry("floating_backpack", IRON, A), new Entry("floating_backpack", IRON, null), new Entry("floating_backpack", IRON, B));
        assertEquals(List.of(0, 1), decide(entries, Set.of(B), Map.of(IRON, 1), Map.of(IRON, 0)));
    }

    @Test
    void nothingIsReturnedForNothing() {
        assertTrue(decide(List.of(), Set.of(), NONE, NONE).isEmpty());
        assertTrue(decide(List.of(new Entry("floating_backpack", IRON, null)), Set.of(), NONE, NONE).isEmpty(),
                "the saved data's own count is missing: nothing is known to be missing");
    }

    @Test
    void theAnswerHasEachPositionOnceAndInOrder() {
        List<Entry> entries = List.of(
                new Entry("floating_backpack", IRON, null), new Entry("floating_backpack", IRON, A), new Entry("floating_backpack", IRON, null));
        List<Integer> result = decide(entries, Set.of(), Map.of(IRON, 3), Map.of(IRON, 0));
        assertEquals(List.of(0, 1, 2), result);
    }
}
