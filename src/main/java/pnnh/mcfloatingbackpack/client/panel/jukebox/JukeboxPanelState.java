package pnnh.mcfloatingbackpack.client.panel.jukebox;

/**
 * 053: whether the player page of the mobile jukebox is expanded, kept for the session (not in the backpack). What it shows is read from the
 * backpack and from the player, so there is nothing else to remember. Closing the page does not stop the music. No Minecraft types, so it can be
 * tested.
 */
public final class JukeboxPanelState {
    private static final JukeboxPanelState INSTANCE = new JukeboxPanelState();

    private boolean open;

    public static JukeboxPanelState get() {
        return INSTANCE;
    }

    public boolean isOpen() {
        return open;
    }

    /** Expands a collapsed page, collapses an expanded one. */
    public void toggle() {
        open = !open;
    }

    public void collapse() {
        open = false;
    }

    /** Forgets everything (leaving the world). */
    public void reset() {
        open = false;
    }
}
