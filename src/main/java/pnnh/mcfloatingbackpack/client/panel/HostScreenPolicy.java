package pnnh.mcfloatingbackpack.client.panel;

/**
 * 019: when the panel needs its transparent host screen. The host is only a fallback, so the one rule is: panel
 * visible and no other screen open. Pure logic with no Minecraft types, so it can be unit-tested.
 */
public final class HostScreenPolicy {
    /** What the game currently shows. */
    public enum ScreenKind {
        /** No screen. */
        NONE,
        /** The panel's own host screen. */
        HOST,
        /** A container screen the panel sits on. */
        CONTAINER,
        /** Anything else (chat, pause, options, death, level transition, another mod's screen). */
        OTHER
    }

    public enum Action {
        NONE,
        OPEN_HOST,
        CLOSE_HOST,
        /** The player is dead: the panel's visible state is cleared. */
        CLEAR_PANEL
    }

    private HostScreenPolicy() {
    }

    /**
     * {@link Action#OPEN_HOST} is only ever returned for {@link ScreenKind#NONE} and {@link Action#CLOSE_HOST} only
     * for {@link ScreenKind#HOST}: the host never covers, and never closes, anyone else's screen.
     */
    public static Action decide(boolean panelVisible, ScreenKind kind, boolean playerAlive) {
        if (!panelVisible) {
            return kind == ScreenKind.HOST ? Action.CLOSE_HOST : Action.NONE;
        }
        if (!playerAlive) {
            return Action.CLEAR_PANEL;
        }
        return kind == ScreenKind.NONE ? Action.OPEN_HOST : Action.NONE;
    }
}
