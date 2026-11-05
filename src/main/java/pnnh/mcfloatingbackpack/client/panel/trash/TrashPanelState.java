package pnnh.mcfloatingbackpack.client.panel.trash;

/**
 * 021: whether the trash can panel is expanded, kept for the session (not in the backpack). What it shows is the
 * backpack's own trash contents, so there is nothing else to remember. No Minecraft types, so it can be tested.
 */
public final class TrashPanelState {
    private static final TrashPanelState INSTANCE = new TrashPanelState();

    private boolean open;

    public static TrashPanelState get() {
        return INSTANCE;
    }

    public boolean isOpen() {
        return open;
    }

    /** Expands a collapsed panel, collapses an expanded one. */
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
