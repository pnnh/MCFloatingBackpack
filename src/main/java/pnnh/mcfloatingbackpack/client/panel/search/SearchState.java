package pnnh.mcfloatingbackpack.client.panel.search;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.PanelSelection;

/**
 * 042: what the search box is doing, kept for the session only (never in the backpack): whether the box is open, whether it has the
 * keyboard, the typed text, and the result list - the backpack slots the main area shows while the query is not empty.
 *
 * <p>The list is worked out when the <em>text changes</em> and not again: a slot whose item is taken away stays in it (an empty place
 * in the same spot, so the item can be put back there), and a slot that starts to match later is not added until the text changes
 * (or the panel is opened again, which starts from nothing). A change of the text also clears the selection and scrolls the main area
 * back to the top. Closing the panel, a new session and leaving the world {@link #reset() reset} everything; the panel merely being
 * hidden does not. Kept free of Minecraft types: what a slot holds is asked of a {@link Source}.
 */
public final class SearchState {
    /** What the state needs to know about the backpack. */
    public interface Source {
        int slotCount();

        /** The searchable text of a slot, or null when it is empty. */
        TextMatcher matcherOf(int slot);

        boolean hasItem(int slot);
    }

    private static final SearchState INSTANCE = new SearchState();

    private final SearchTextInput input = new SearchTextInput();
    private boolean open;
    private boolean focused;
    private String lastText = "";
    private SearchQuery query = SearchQuery.empty();
    private int[] slots;

    public static SearchState get() {
        return INSTANCE;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isFocused() {
        return open && focused;
    }

    public SearchTextInput input() {
        return input;
    }

    public SearchQuery query() {
        return query;
    }

    /** Whether the main area is being filtered (a non-empty query). */
    public boolean isFiltering() {
        return open && slots != null;
    }

    /** The slots the main area shows, ascending; null when nothing is filtered. */
    public int[] slots() {
        return slots;
    }

    /** The magnifier was clicked: the box opens with the keyboard. */
    public void openBox() {
        open = true;
        focused = true;
    }

    /** A click in the box. */
    public void focus() {
        if (open) {
            focused = true;
        }
    }

    /** A press anywhere but in the box: the box stays, with its text and results, but the keyboard is the game's again. */
    public void loseFocus() {
        focused = false;
    }

    /** Esc, or the clear button: the text is cleared, the box closes, and the main area shows everything again. */
    public void close(Source source) {
        input.clear();
        open = false;
        focused = false;
        textChanged(source);
    }

    /** Call after every edit of the text: if the text really is different the query, the results, the selection and the scroll follow. */
    public void textChanged(Source source) {
        String text = input.text();
        if (text.equals(lastText)) {
            return;
        }
        lastText = text;
        query = SearchQuery.parse(text);
        int[] found = SearchResults.compute(query, source.slotCount(), source::matcherOf);
        slots = found;
        PanelSelection.get().clear();
        FloatingPanelState state = FloatingPanelState.get();
        state.setSearchSlots(found);
        state.setScrollRow(0);
    }

    /** The number shown in the box: the listed slots that hold an item right now. */
    public int liveCount(Source source) {
        return SearchResults.liveCount(slots, source::hasItem);
    }

    /** Forgets everything (the panel closed, a new session, leaving the world). */
    public void reset() {
        input.clear();
        open = false;
        focused = false;
        lastText = "";
        query = SearchQuery.empty();
        slots = null;
        FloatingPanelState.get().setSearchSlots(null);
    }
}
