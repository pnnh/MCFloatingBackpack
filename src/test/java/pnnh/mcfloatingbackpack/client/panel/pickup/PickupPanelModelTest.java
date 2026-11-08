package pnnh.mcfloatingbackpack.client.panel.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 024: what the blacklist page shows and offers. */
class PickupPanelModelTest {
    private static List<String> tags(int n) {
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tags.add("t" + i);
        }
        return tags;
    }

    @Test
    void canAddNeedsTheToolAndRoom() {
        assertTrue(PickupPanelModel.of(tags(3), true).canAdd());
        assertFalse(PickupPanelModel.of(tags(3), false).canAdd());
        assertTrue(PickupPanelModel.of(tags(63), true).canAdd());
        assertFalse(PickupPanelModel.of(tags(64), true).canAdd());
    }

    @Test
    void countsAndFullness() {
        PickupPanelModel model = PickupPanelModel.of(tags(64), true);
        assertTrue(model.isFull());
        assertEquals(64, model.count());
        assertEquals(64, model.max());
        assertFalse(PickupPanelModel.of(tags(10), true).isFull());
        assertEquals(List.of(), PickupPanelModel.of(List.of(), true).tags());
    }

    @Test
    void candidatesAreThePresetsThenTheTagsInUseWithoutDuplicates() {
        List<String> out = PickupPanelModel.candidates(
                List.of("stone", "wood", "trash"), List.of("mine", "wood", "keep", "mine"), List.of());
        assertEquals(List.of("stone", "wood", "trash", "mine", "keep"), out);
    }

    @Test
    void candidatesLeaveOutWhatIsAlreadyOnTheBlacklist() {
        List<String> out = PickupPanelModel.candidates(
                List.of("stone", "wood", "trash"), List.of("mine", "keep"), List.of("wood", "mine"));
        assertEquals(List.of("stone", "trash", "keep"), out);
    }
}
