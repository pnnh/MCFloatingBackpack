package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.combined.CombinedMembers.Candidate;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedMembers.Place;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedMembers.Result;

/** 065: the member table: order, what is left out and why, and that equal inputs give equal results. */
class CombinedMembersTest {
    private static final int LEATHER = 0;
    private static final int IRON = 1;
    private static final int GOLD = 2;
    private static final int NETHERITE = 4;

    private static Candidate<String> cand(String ref, int tier, boolean newer) {
        return new Candidate<>(ref, new Place(Place.Kind.INVENTORY, 0, 0), UUID.nameUUIDFromBytes(ref.getBytes()), tier, "", newer);
    }

    @Test
    void membersKeepTheOrderTheyCameIn() {
        Result<String> result = CombinedMembers.compute(List.of(cand("a", IRON, false), cand("b", LEATHER, false), cand("c", IRON, false)), NETHERITE);
        assertEquals(List.of("a", "b", "c"), result.members().stream().map(m -> m.ref()).toList());
        assertEquals(0, result.skippedTotal());
    }

    @Test
    void aHigherTierThanTheCombinedBackpackIsLeftOutAndCounted() {
        Result<String> result = CombinedMembers.compute(List.of(cand("a", LEATHER, false), cand("b", IRON, false), cand("c", GOLD, false)), IRON);
        assertEquals(List.of("a", "b"), result.members().stream().map(m -> m.ref()).toList());
        assertEquals(1, result.skippedTier());
    }

    @Test
    void theSameTierIsConnected() {
        Result<String> result = CombinedMembers.compute(List.of(cand("a", IRON, false)), IRON);
        assertEquals(1, result.members().size());
    }

    @Test
    void aBackpackSavedByANewerVersionIsLeftOutEvenIfItsTierFits() {
        Result<String> result = CombinedMembers.compute(List.of(cand("a", LEATHER, true), cand("b", LEATHER, false)), NETHERITE);
        assertEquals(List.of("b"), result.members().stream().map(m -> m.ref()).toList());
        assertEquals(1, result.skippedNewer());
        assertEquals(0, result.skippedTier());
    }

    @Test
    void aNewerBackpackOfAHigherTierIsCountedAsNewerOnly() {
        Result<String> result = CombinedMembers.compute(List.of(cand("a", GOLD, true)), LEATHER);
        assertEquals(1, result.skippedNewer());
        assertEquals(0, result.skippedTier());
    }

    @Test
    void onlyTheFirstSixteenAreConnectedAndTheRestCounted() {
        List<Candidate<String>> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add(cand("b" + i, LEATHER, false));
        }
        Result<String> result = CombinedMembers.compute(candidates, NETHERITE);
        assertEquals(CombinedMembers.MAX_MEMBERS, result.members().size());
        assertEquals("b15", result.members().get(15).ref());
        assertEquals(4, result.skippedOverCap());
    }

    @Test
    void leftOutBackpacksDoNotUseUpTheSixteenPlaces() {
        List<Candidate<String>> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candidates.add(cand("high" + i, NETHERITE, false));
        }
        for (int i = 0; i < 16; i++) {
            candidates.add(cand("low" + i, LEATHER, false));
        }
        Result<String> result = CombinedMembers.compute(candidates, LEATHER);
        assertEquals(16, result.members().size());
        assertEquals(10, result.skippedTier());
        assertEquals(0, result.skippedOverCap());
    }

    @Test
    void theSameIdentityMayAppearTwice() {
        UUID same = UUID.randomUUID();
        Candidate<String> one = new Candidate<>("a", new Place(Place.Kind.INVENTORY, 0, 1), same, LEATHER, "", false);
        Candidate<String> two = new Candidate<>("b", new Place(Place.Kind.INVENTORY, 0, 2), same, LEATHER, "", false);
        assertEquals(2, CombinedMembers.compute(List.of(one, two), LEATHER).members().size());
    }

    @Test
    void equalInputsGiveEqualResultsAndAChangeShowsUp() {
        List<Candidate<String>> candidates = List.of(cand("a", LEATHER, false), cand("b", IRON, false));
        assertEquals(CombinedMembers.compute(candidates, IRON), CombinedMembers.compute(candidates, IRON));
        Candidate<String> renamed = new Candidate<>("a", new Place(Place.Kind.INVENTORY, 0, 0), UUID.nameUUIDFromBytes("a".getBytes()), LEATHER, "Food", false);
        assertEquals(false, CombinedMembers.compute(candidates, IRON).equals(CombinedMembers.compute(List.of(renamed, candidates.get(1)), IRON)));
    }

    @Test
    void noCandidatesGiveAnEmptyResult() {
        Result<String> result = CombinedMembers.compute(List.<Candidate<String>>of(), IRON);
        assertEquals(0, result.members().size());
        assertEquals(0, result.skippedTotal());
    }
}
