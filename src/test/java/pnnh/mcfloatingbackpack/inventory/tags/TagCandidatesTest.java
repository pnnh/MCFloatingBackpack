package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 025: what the text boxes' drop-downs start from. */
class TagCandidatesTest {
    @Test
    void presetsComeFirstThenTheTagsInUseEachOnce() {
        assertEquals(
                List.of("stone", "wood", "trash", "mine", "keep"),
                TagCandidates.of(List.of("stone", "wood", "trash"), List.of("mine", "wood", "keep", "mine"), List.of()));
    }

    @Test
    void excludedTagsAreLeftOut() {
        assertEquals(
                List.of("stone", "trash", "keep"),
                TagCandidates.of(List.of("stone", "wood", "trash"), List.of("mine", "keep"), List.of("wood", "mine")));
    }

    @Test
    void nothingGivenMeansNothingOffered() {
        assertEquals(List.of(), TagCandidates.of(List.of(), List.of(), List.of()));
    }
}
