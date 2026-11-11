package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewState.View;

class TagOverviewStateTest {
    // The states are singletons shared with other tests in this JVM.
    @BeforeEach
    @AfterEach
    void reset() {
        TagOverviewState.get().reset();
        TagPanelState.get().reset();
    }

    @Test
    void startsInTheSelectionView() {
        assertEquals(View.SELECTION, TagOverviewState.get().view());
    }

    @Test
    void movesBetweenTheViews() {
        TagOverviewState state = TagOverviewState.get();
        state.enterList();
        assertEquals(View.LIST, state.view());
        state.enterDetail("minecraft:stone");
        assertEquals(View.DETAIL, state.view());
        assertEquals("minecraft:stone", state.detailItem());
        state.backToList();
        assertEquals(View.LIST, state.view());
        assertNull(state.detailItem());
        state.toSelection();
        assertEquals(View.SELECTION, state.view());
    }

    @Test
    void goingBackToTheListKeepsTheScrollAndTheFilter() {
        TagOverviewState state = TagOverviewState.get();
        state.enterList();
        state.setListScroll(7);
        state.setFilter("trash");
        state.enterDetail("minecraft:stone");
        state.backToList();
        assertEquals(7, state.listScroll());
        assertEquals("trash", state.filter());
    }

    @Test
    void resetViewForgetsTheViewAndTheTypedFilterText() {
        TagOverviewState state = TagOverviewState.get();
        state.enterList();
        state.setListScroll(7);
        state.setFilter("trash");
        state.filterInput().paste("abc");
        state.startFilterEditing();
        state.confirm().arm("a", 0);
        state.resetView();
        assertEquals(View.SELECTION, state.view());
        assertEquals(0, state.listScroll());
        assertNull(state.filter());
        assertFalse(state.isFilterEditing());
        assertFalse(state.confirm().isArmed("a", 0));
        assertEquals("", state.filterInput().text());
    }

    @Test
    void resetAlsoClearsTheFilterText() {
        TagOverviewState state = TagOverviewState.get();
        state.filterInput().paste("abc");
        state.reset();
        assertEquals("", state.filterInput().text());
    }

    @Test
    void filterCanBeSetAndCleared() {
        TagOverviewState state = TagOverviewState.get();
        state.setFilter("trash");
        assertEquals("trash", state.filter());
        state.clearFilter();
        assertNull(state.filter());
    }

    @Test
    void theFilterBoxOnlyHasTheFocusOnTheList() {
        TagOverviewState state = TagOverviewState.get();
        state.startFilterEditing();
        assertFalse(state.isFilterEditing());
        state.enterList();
        state.startFilterEditing();
        assertTrue(state.isFilterEditing());
        state.filterInput().paste("abc");
        state.stopFilterEditing();
        assertFalse(state.isFilterEditing());
        assertEquals("", state.filterInput().text());
    }

    @Test
    void leavingTheListDropsTheUnchosenFilterText() {
        TagOverviewState state = TagOverviewState.get();
        state.enterList();
        state.startFilterEditing();
        state.filterInput().paste("abc");
        state.enterDetail("minecraft:stone");
        state.backToList();
        assertEquals("", state.filterInput().text());
    }

    @Test
    void theListScrollIsNeverNegative() {
        TagOverviewState state = TagOverviewState.get();
        state.setListScroll(-3);
        assertEquals(0, state.listScroll());
    }

    @Test
    void everySwitchOfViewDropsTheRowConfirmationAndTheDetailBoxState() {
        TagOverviewState state = TagOverviewState.get();
        state.enterList();
        state.confirm().arm("a", 0);
        state.enterDetail("minecraft:stone");
        assertFalse(state.confirm().isArmed("a", 0));

        TagPanelState tags = TagPanelState.get();
        tags.toggle();
        tags.setScroll(4);
        tags.setPendingDelete("wood");
        tags.startEditing();
        state.backToList();
        assertFalse(tags.isEditing());
        assertNull(tags.pendingDelete());
        assertEquals(0, tags.scroll());

        state.confirm().arm("b", 0);
        state.toSelection();
        assertFalse(state.confirm().isArmed("b", 0));
    }

    @Test
    void theConfirmationIsOneInstance() {
        assertSame(TagOverviewState.get().confirm(), TagOverviewState.get().confirm());
    }

    @Test
    void isPristineOnlyWhenNothingOfTheOverviewIsRemembered() {
        TagOverviewState state = TagOverviewState.get();
        assertTrue(state.isPristine());
        state.setFilter("trash");
        assertFalse(state.isPristine());
        state.resetView();
        assertTrue(state.isPristine());
        state.enterList();
        assertFalse(state.isPristine());
        state.toSelection();
        assertTrue(state.isPristine());
        state.setListScroll(2);
        assertFalse(state.isPristine());
    }
}
