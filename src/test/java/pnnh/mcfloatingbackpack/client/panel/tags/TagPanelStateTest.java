package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagPanelStateTest {
    // The state is a singleton shared with other tests in this JVM.
    @BeforeEach
    @AfterEach
    void reset() {
        TagPanelState.get().reset();
    }

    @Test
    void toggleExpandsAndCollapses() {
        TagPanelState state = TagPanelState.get();
        assertFalse(state.isOpen());
        state.toggle();
        assertTrue(state.isOpen());
        state.toggle();
        assertFalse(state.isOpen());
    }

    @Test
    void collapseEndsEditingButKeepsTheScroll() {
        TagPanelState state = TagPanelState.get();
        state.toggle();
        state.setScroll(3);
        state.input().paste("abc");
        state.startEditing();
        state.setPendingDelete("wood");
        state.collapse();
        assertFalse(state.isOpen());
        assertFalse(state.isEditing());
        assertNull(state.pendingDelete());
        assertEquals(3, state.scroll());
        assertEquals("abc", state.input().text(), "the typed text survives collapsing");
    }

    @Test
    void resetClearsEverything() {
        TagPanelState state = TagPanelState.get();
        state.toggle();
        state.setScroll(5);
        state.input().paste("abc");
        state.reset();
        assertFalse(state.isOpen());
        assertEquals(0, state.scroll());
        assertEquals("", state.input().text());
    }

    @Test
    void onlyAnExpandedPanelCanHaveTheTextBoxFocus() {
        TagPanelState state = TagPanelState.get();
        state.startEditing();
        assertFalse(state.isEditing());
        state.toggle();
        state.startEditing();
        assertTrue(state.isEditing());
        state.stopEditing();
        assertFalse(state.isEditing());
    }

    @Test
    void startingToEditDropsAPendingDelete() {
        TagPanelState state = TagPanelState.get();
        state.toggle();
        state.setPendingDelete("wood");
        state.startEditing();
        assertNull(state.pendingDelete());
        assertEquals(-1, state.dropdownIndex());
    }
}
