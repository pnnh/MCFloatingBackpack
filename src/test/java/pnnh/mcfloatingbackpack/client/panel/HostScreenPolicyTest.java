package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.HostScreenPolicy.Action;
import pnnh.mcfloatingbackpack.client.panel.HostScreenPolicy.ScreenKind;

class HostScreenPolicyTest {
    @Test
    void visibleAndAliveOpensTheHostOnlyWhenThereIsNoScreen() {
        assertEquals(Action.OPEN_HOST, HostScreenPolicy.decide(true, ScreenKind.NONE, true));
        assertEquals(Action.NONE, HostScreenPolicy.decide(true, ScreenKind.HOST, true));
        assertEquals(Action.NONE, HostScreenPolicy.decide(true, ScreenKind.CONTAINER, true));
        assertEquals(Action.NONE, HostScreenPolicy.decide(true, ScreenKind.OTHER, true));
    }

    @Test
    void visibleButDeadClearsThePanelWhateverTheScreen() {
        for (ScreenKind kind : ScreenKind.values()) {
            assertEquals(Action.CLEAR_PANEL, HostScreenPolicy.decide(true, kind, false), kind.name());
        }
    }

    @Test
    void hiddenPanelClosesTheHostAndDoesNothingElse() {
        for (boolean alive : new boolean[] {true, false}) {
            assertEquals(Action.CLOSE_HOST, HostScreenPolicy.decide(false, ScreenKind.HOST, alive));
            assertEquals(Action.NONE, HostScreenPolicy.decide(false, ScreenKind.NONE, alive));
            assertEquals(Action.NONE, HostScreenPolicy.decide(false, ScreenKind.CONTAINER, alive));
            assertEquals(Action.NONE, HostScreenPolicy.decide(false, ScreenKind.OTHER, alive));
        }
    }

    @Test
    void theHostIsNeverOpenedOverAnotherScreen() {
        for (boolean visible : new boolean[] {true, false}) {
            for (boolean alive : new boolean[] {true, false}) {
                for (ScreenKind kind : ScreenKind.values()) {
                    if (kind != ScreenKind.NONE) {
                        assertNotEquals(Action.OPEN_HOST, HostScreenPolicy.decide(visible, kind, alive));
                    }
                }
            }
        }
    }

    @Test
    void onlyTheHostItselfIsEverClosed() {
        for (boolean visible : new boolean[] {true, false}) {
            for (boolean alive : new boolean[] {true, false}) {
                for (ScreenKind kind : ScreenKind.values()) {
                    if (kind != ScreenKind.HOST) {
                        assertNotEquals(Action.CLOSE_HOST, HostScreenPolicy.decide(visible, kind, alive));
                    }
                }
            }
        }
    }

    @Test
    void aHiddenPanelNeverTriggersAnythingButClosingTheHost() {
        for (boolean alive : new boolean[] {true, false}) {
            for (ScreenKind kind : ScreenKind.values()) {
                Action action = HostScreenPolicy.decide(false, kind, alive);
                assertEquals(kind == ScreenKind.HOST ? Action.CLOSE_HOST : Action.NONE, action);
            }
        }
    }
}
