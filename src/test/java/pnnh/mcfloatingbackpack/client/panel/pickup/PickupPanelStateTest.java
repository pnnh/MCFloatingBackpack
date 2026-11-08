package pnnh.mcfloatingbackpack.client.panel.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;

class PickupPanelStateTest {
    // The states are singletons shared with other tests in this JVM.
    @BeforeEach
    @AfterEach
    void reset() {
        PickupPanelState.get().reset();
        TagPanelState.get().reset();
    }

    @Test
    void toggleExpandsAndCollapses() {
        PickupPanelState state = PickupPanelState.get();
        assertFalse(state.isOpen());
        state.toggle();
        assertTrue(state.isOpen());
        state.toggle();
        assertFalse(state.isOpen());
    }

    @Test
    void collapseEndsEditingButKeepsTheScrollAndTheText() {
        PickupPanelState state = PickupPanelState.get();
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
        assertEquals("abc", state.input().text());
    }

    @Test
    void resetClearsEverything() {
        PickupPanelState state = PickupPanelState.get();
        state.toggle();
        state.setScroll(5);
        state.input().paste("abc");
        state.reset();
        assertFalse(state.isOpen());
        assertEquals(0, state.scroll());
        assertEquals("", state.input().text());
    }

    @Test
    void onlyAnExpandedPageCanHaveTheTextBoxFocus() {
        PickupPanelState state = PickupPanelState.get();
        state.startEditing();
        assertFalse(state.isEditing());
        state.toggle();
        state.startEditing();
        assertTrue(state.isEditing());
        state.stopEditing();
        assertFalse(state.isEditing());
    }

    @Test
    void scrollIsNeverNegative() {
        PickupPanelState state = PickupPanelState.get();
        state.setScroll(-4);
        assertEquals(0, state.scroll());
    }

    @Test
    void expandingTheBlacklistPageCollapsesTheTagPanelAndTheOtherWayRound() {
        TagPanelState tags = TagPanelState.get();
        PickupPanelState pickup = PickupPanelState.get();
        tags.toggle();
        assertTrue(tags.isOpen());
        pickup.toggle();
        assertTrue(pickup.isOpen());
        assertFalse(tags.isOpen());
        tags.toggle();
        assertTrue(tags.isOpen());
        assertFalse(pickup.isOpen());
    }

    @Test
    void collapsingOneDoesNotTouchTheOther() {
        PickupPanelState pickup = PickupPanelState.get();
        pickup.toggle();
        TagPanelState.get().collapse();
        assertTrue(pickup.isOpen());
    }
}
