package pnnh.mcfloatingbackpack.client.panel.trash;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 021: the trash can panel's session state. */
class TrashPanelStateTest {
    @Test
    void toggleSwitchesBetweenOpenAndCollapsed() {
        TrashPanelState state = TrashPanelState.get();
        state.reset();
        assertFalse(state.isOpen());
        state.toggle();
        assertTrue(state.isOpen());
        state.toggle();
        assertFalse(state.isOpen());
    }

    @Test
    void collapseAndResetCloseIt() {
        TrashPanelState state = TrashPanelState.get();
        state.reset();
        state.toggle();
        state.collapse();
        assertFalse(state.isOpen());
        state.toggle();
        state.reset();
        assertFalse(state.isOpen());
    }
}
