package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PanelSelectionTest {
    private final PanelSelection selection = new PanelSelection();

    private void context(int slotCount) {
        selection.syncContext(true, "a", slotCount);
    }

    @Test
    void startsEmpty() {
        assertTrue(selection.isEmpty());
        assertEquals(0, selection.snapshot().length);
    }

    @Test
    void toggleAddsThenRemoves() {
        context(9);
        assertTrue(selection.toggle(3));
        assertTrue(selection.contains(3));
        assertFalse(selection.toggle(3));
        assertFalse(selection.contains(3));
        assertTrue(selection.isEmpty());
    }

    @Test
    void addAllOnlyAddsAndIgnoresRepeats() {
        context(9);
        selection.toggle(1);
        selection.addAll(List.of(1, 2, 2, 5));
        assertArrayEquals(new int[] {1, 2, 5}, selection.snapshot());
    }

    @Test
    void indexesOutsideTheBackpackAreNeverSelected() {
        context(5);
        assertFalse(selection.toggle(5));
        assertFalse(selection.toggle(-1));
        selection.addAll(List.of(4, 5, 99, -3));
        assertArrayEquals(new int[] {4}, selection.snapshot());
    }

    @Test
    void emptySlotsAreSelectableBecauseTheSelectionKnowsNothingAboutItems() {
        context(9);
        // The selection API has no notion of slot contents at all: any in-range index can be selected.
        selection.addAll(List.of(0, 8));
        assertTrue(selection.contains(0));
        assertTrue(selection.contains(8));
    }

    @Test
    void snapshotIsAscendingAndACopy() {
        context(20);
        selection.addAll(List.of(9, 2, 15, 4));
        int[] first = selection.snapshot();
        assertArrayEquals(new int[] {2, 4, 9, 15}, first);
        first[0] = 99;
        assertNotSame(first, selection.snapshot());
        assertArrayEquals(new int[] {2, 4, 9, 15}, selection.snapshot());
    }

    @Test
    void clearEmptiesTheSelection() {
        context(9);
        selection.addAll(List.of(1, 2));
        selection.clear();
        assertTrue(selection.isEmpty());
    }

    @Test
    void contextChangesClear() {
        selection.syncContext(true, "a", 9);
        selection.addAll(List.of(1, 2));

        selection.syncContext(true, "a", 9); // nothing changed
        assertArrayEquals(new int[] {1, 2}, selection.snapshot());

        selection.syncContext(true, "b", 9); // another backpack
        assertTrue(selection.isEmpty());

        selection.addAll(List.of(3));
        selection.syncContext(true, "b", 18); // capacity changed
        assertTrue(selection.isEmpty());

        selection.addAll(List.of(3));
        selection.syncContext(false, "b", 18); // panel hidden
        assertTrue(selection.isEmpty());
    }

    @Test
    void aSelectionSurvivesWhenTheSameContextIsReportedAgain() {
        selection.syncContext(true, "a", 9);
        selection.addAll(List.of(7));
        for (int i = 0; i < 10; i++) {
            selection.syncContext(true, "a", 9);
        }
        assertTrue(selection.contains(7));
    }

    @Test
    void afterBeingHiddenTheNextVisibleContextStartsFresh() {
        selection.syncContext(true, "a", 9);
        selection.addAll(List.of(1));
        selection.syncContext(false, null, 0);
        selection.syncContext(true, "a", 9);
        assertTrue(selection.isEmpty());
        selection.addAll(List.of(2));
        assertTrue(selection.contains(2));
    }

    @Test
    void beforeAnyContextIsKnownTheCallersBoundsApply() {
        // Callers only pass real slots (0 <= index < slotCount), so nothing is rejected before the first sync.
        assertTrue(selection.toggle(40));
    }
}
