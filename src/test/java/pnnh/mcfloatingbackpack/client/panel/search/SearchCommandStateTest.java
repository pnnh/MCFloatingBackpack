package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.PanelSelection;
import pnnh.mcfloatingbackpack.client.panel.search.HiddenSummaryRows.Line;

/** 044: the session state while the query begins with a command. */
class SearchCommandStateTest {
    private static final List<Line> HIDDEN = List.of(new Line("food", "Food", 3), new Line("fishing", "Fishing", 2));

    private static final class Backpack implements SearchState.Source {
        final String[] names;
        final List<Line> hidden;

        Backpack(List<Line> hidden, String... names) {
            this.hidden = hidden;
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

        @Override
        public List<Line> hiddenLines() {
            return hidden;
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
        clean();
    }

    private void type(SearchState.Source source, String text) {
        state.input().clear();
        text.codePoints().forEach(state.input()::insert);
        state.textChanged(source);
    }

    @Test
    void hiddenShowsNoCellsButCountsTheHiddenCells() {
        Backpack backpack = new Backpack(HIDDEN, "oak log", "apple");
        state.openBox();
        type(backpack, "/hidden");
        assertTrue(state.isFiltering());
        assertArrayEquals(new int[0], state.slots());
        assertArrayEquals(new int[0], FloatingPanelState.get().getSearchSlots());
        assertEquals(SearchQuery.Command.Type.HIDDEN, state.command().type());
        assertEquals(5, state.liveCount(backpack));
        assertEquals(HIDDEN, state.hiddenRows(backpack));
    }

    @Test
    void theWordsAfterHiddenFilterTheLinesAndTheCount() {
        Backpack backpack = new Backpack(HIDDEN, "oak log");
        state.openBox();
        type(backpack, "/hidden fish");
        assertEquals(List.of(HIDDEN.get(1)), state.hiddenRows(backpack));
        assertEquals(2, state.liveCount(backpack));
        type(backpack, "/hidden @mod");
        assertEquals(List.of(), state.hiddenRows(backpack));
        assertEquals(0, state.liveCount(backpack));
    }

    @Test
    void hiddenWithNothingHiddenCountsZeroAndIsStillAFilteringState() {
        Backpack backpack = new Backpack(List.of(), "oak log");
        state.openBox();
        type(backpack, "/hidden");
        assertTrue(state.isFiltering());
        assertEquals(0, state.liveCount(backpack));
        assertEquals(List.of(), state.hiddenRows(backpack));
    }

    @Test
    void anUnknownCommandFindsNothingAndCountsZero() {
        Backpack backpack = new Backpack(HIDDEN, "oak log");
        state.openBox();
        for (String input : new String[] {"/foo", "/", "/h", "/hiddenx"}) {
            type(backpack, input);
            assertTrue(state.isFiltering(), input);
            assertArrayEquals(new int[0], state.slots(), input);
            assertEquals(SearchQuery.Command.Type.UNKNOWN, state.command().type(), input);
            assertEquals(0, state.liveCount(backpack), input);
        }
        type(backpack, "/Foo bar");
        assertEquals("/Foo", state.command().raw());
    }

    @Test
    void enteringACommandClearsTheSelectionAndScrollsBackLikeAnyQueryChange() {
        Backpack backpack = new Backpack(HIDDEN, "oak log", "apple");
        state.openBox();
        PanelSelection.get().toggle(0);
        FloatingPanelState.get().setScrollRow(3);
        type(backpack, "/hidden");
        assertTrue(PanelSelection.get().isEmpty());
        assertEquals(0, FloatingPanelState.get().getScrollRow());
    }

    @Test
    void leavingACommandShowsTheCellsAgain() {
        Backpack backpack = new Backpack(HIDDEN, "oak log", "apple");
        state.openBox();
        type(backpack, "/hidden");
        type(backpack, "oak");
        assertEquals(SearchQuery.Command.Type.NONE, state.command().type());
        assertArrayEquals(new int[] {0}, state.slots());
        assertEquals(1, state.liveCount(backpack));
        type(backpack, "/hidden");
        state.close(backpack);
        assertFalse(state.isFiltering());
        assertEquals(null, state.slots());
        assertEquals(SearchQuery.Command.Type.NONE, state.command().type());
    }

    @Test
    void aSourceThatDoesNotKnowHiddenLinesSaysNothingIsHidden() {
        SearchState.Source old = new SearchState.Source() {
            @Override
            public int slotCount() {
                return 1;
            }

            @Override
            public TextMatcher matcherOf(int slot) {
                return TextMatcher.ofTexts(List.of("oak log"));
            }

            @Override
            public boolean hasItem(int slot) {
                return true;
            }
        };
        state.openBox();
        type(old, "/hidden");
        assertEquals(0, state.liveCount(old));
        assertEquals(List.of(), state.hiddenRows(old));
    }

    @Test
    void anOrdinaryQueryCountsLikeBefore() {
        Backpack backpack = new Backpack(HIDDEN, "oak log", "apple", "oak planks");
        state.openBox();
        type(backpack, "oak");
        assertEquals(2, state.liveCount(backpack));
        type(backpack, "@minecraft");
        assertEquals(0, state.liveCount(backpack), "a plain matcher knows no mods");
    }

    @Test
    void resetForgetsTheCommand() {
        Backpack backpack = new Backpack(HIDDEN, "oak log");
        state.openBox();
        type(backpack, "/hidden");
        state.reset();
        assertEquals(SearchQuery.Command.Type.NONE, state.command().type());
        assertFalse(state.isFiltering());
    }
}
