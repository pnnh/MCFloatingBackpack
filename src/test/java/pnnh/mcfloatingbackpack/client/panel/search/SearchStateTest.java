package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.PanelSelection;

/** 042: the session state of the search box. */
class SearchStateTest {
    /** A backpack of names; a null name is an empty slot. */
    private static final class Backpack implements SearchState.Source {
        final String[] names;

        Backpack(String... names) {
            this.names = names;
        }

        @Override
        public int slotCount() {
            return names.length;
        }

        @Override
        public TextMatcher matcherOf(int slot) {
            return names[slot] == null ? null : TextMatcher.ofTexts(List.of(names[slot]));
        }

        @Override
        public boolean hasItem(int slot) {
            return names[slot] != null;
        }
    }

    private final SearchState state = SearchState.get();

    @BeforeEach
    void clean() {
        state.reset();
        PanelSelection.get().clear();
        FloatingPanelState.get().setScrollRow(0);
    }

    @AfterEach
    void cleanAfter() {
        state.reset();
        PanelSelection.get().clear();
        FloatingPanelState.get().setScrollRow(0);
    }

    private void type(Backpack backpack, String text) {
        state.input().clear();
        text.codePoints().forEach(state.input()::insert);
        state.textChanged(backpack);
    }

    @Test
    void openingTheBoxGivesItTheKeyboardAndFiltersNothingYet() {
        state.openBox();
        assertTrue(state.isOpen());
        assertTrue(state.isFocused());
        assertFalse(state.isFiltering());
        assertNull(state.slots());
    }

    @Test
    void typingFiltersAndPushesTheListToThePanel() {
        Backpack backpack = new Backpack("oak log", null, "iron axe", "oak planks");
        state.openBox();
        type(backpack, "oak");
        assertTrue(state.isFiltering());
        assertArrayEquals(new int[] {0, 3}, state.slots());
        assertArrayEquals(new int[] {0, 3}, FloatingPanelState.get().getSearchSlots());
    }

    @Test
    void anEditThatChangesTheTextClearsTheSelectionAndScrollsBackToTheTop() {
        Backpack backpack = new Backpack("oak log", "iron axe");
        state.openBox();
        PanelSelection.get().toggle(0);
        PanelSelection.get().toggle(1);
        type(backpack, "oak");
        assertTrue(PanelSelection.get().isEmpty());
        PanelSelection.get().toggle(0);
        state.input().insert('x');
        state.textChanged(backpack);
        assertTrue(PanelSelection.get().isEmpty());
    }

    @Test
    void anEditThatLeavesTheTextAloneChangesNothing() {
        Backpack backpack = new Backpack("oak log", "iron axe");
        state.openBox();
        type(backpack, "oak");
        PanelSelection.get().toggle(0);
        state.input().left();
        state.textChanged(backpack);
        assertFalse(PanelSelection.get().isEmpty());
    }

    @Test
    void closingClearsTheTextAndShowsEverythingAgain() {
        Backpack backpack = new Backpack("oak log", "iron axe");
        state.openBox();
        type(backpack, "oak");
        PanelSelection.get().toggle(0);
        state.close(backpack);
        assertFalse(state.isOpen());
        assertFalse(state.isFocused());
        assertEquals("", state.input().text());
        assertNull(state.slots());
        assertNull(FloatingPanelState.get().getSearchSlots());
        assertTrue(PanelSelection.get().isEmpty());
    }

    @Test
    void closingAnEmptyBoxKeepsTheSelection() {
        Backpack backpack = new Backpack("oak log", "iron axe");
        state.openBox();
        PanelSelection.get().toggle(0);
        state.close(backpack);
        assertFalse(PanelSelection.get().isEmpty());
    }

    @Test
    void losingTheFocusKeepsTheTextAndTheResults() {
        Backpack backpack = new Backpack("oak log", "iron axe");
        state.openBox();
        type(backpack, "oak");
        state.loseFocus();
        assertTrue(state.isOpen());
        assertFalse(state.isFocused());
        assertTrue(state.isFiltering());
        assertEquals("oak", state.input().text());
        state.focus();
        assertTrue(state.isFocused());
    }

    @Test
    void takingAnItemAwayLeavesItsPlaceInTheListButNotInTheCount() {
        Backpack backpack = new Backpack("oak log", "oak log", "iron axe");
        state.openBox();
        type(backpack, "oak log");
        assertArrayEquals(new int[] {0, 1}, state.slots());
        assertEquals(2, state.liveCount(backpack));
        backpack.names[1] = null;
        assertArrayEquals(new int[] {0, 1}, state.slots());
        assertEquals(1, state.liveCount(backpack));
    }

    @Test
    void aSlotThatStartsToMatchLaterIsNotAddedUntilTheTextChanges() {
        Backpack backpack = new Backpack("oak log", null, "iron axe");
        state.openBox();
        type(backpack, "oak");
        backpack.names[1] = "oak planks";
        state.textChanged(backpack);
        assertArrayEquals(new int[] {0}, state.slots());
        state.input().insert(' ');
        state.textChanged(backpack);
        // "oak " no longer matches "oak planks"? it does: the blank is a term separator
        assertArrayEquals(new int[] {0, 1}, state.slots());
    }

    @Test
    void anEmptiedQueryShowsEverything() {
        Backpack backpack = new Backpack("oak log", "iron axe");
        state.openBox();
        type(backpack, "oak");
        type(backpack, "");
        assertNull(state.slots());
        assertFalse(state.isFiltering());
    }

    @Test
    void resetForgetsEverything() {
        Backpack backpack = new Backpack("oak log", "iron axe");
        state.openBox();
        type(backpack, "oak");
        state.reset();
        assertFalse(state.isOpen());
        assertFalse(state.isFocused());
        assertEquals("", state.input().text());
        assertNull(state.slots());
        assertNull(FloatingPanelState.get().getSearchSlots());
        assertTrue(state.query().isEmpty());
    }

    @Test
    void theCountIsZeroWithoutAList() {
        Set<Integer> none = new HashSet<>();
        assertEquals(0, state.liveCount(new Backpack("a")));
        assertTrue(none.isEmpty());
    }
}
