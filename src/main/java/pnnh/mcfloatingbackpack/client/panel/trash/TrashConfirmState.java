package pnnh.mcfloatingbackpack.client.panel.trash;

/**
 * 022: the operation that waits for the player's confirmation because it would destroy more than nine groups for good.
 * Session state (never saved). It remembers what to send when the player says yes, how many groups the dialog names,
 * and which screen the dialog opened on, so that changing screens drops the question. No Minecraft types, so it can be
 * tested; the screen is an opaque object compared by identity.
 */
public final class TrashConfirmState {
    private static final TrashConfirmState INSTANCE = new TrashConfirmState();

    /** What is waiting. */
    public enum Kind {
        /** A dump of the trash-tagged stacks; the server picks them again when it gets the confirmed request. */
        DUMP,
        /** The stacks in these main-area slots (taken when the question was asked). */
        MAIN_SLOTS
    }

    private boolean pending;
    private Kind kind = Kind.DUMP;
    private int[] slots = new int[0];
    private int destroyed;
    private Object screen;

    public static TrashConfirmState get() {
        return INSTANCE;
    }

    public void open(Kind kind, int[] slots, int destroyed, Object screen) {
        this.pending = true;
        this.kind = kind;
        this.slots = slots == null ? new int[0] : slots.clone();
        this.destroyed = destroyed;
        this.screen = screen;
    }

    public void cancel() {
        pending = false;
        kind = Kind.DUMP;
        slots = new int[0];
        destroyed = 0;
        screen = null;
    }

    public boolean isPending() {
        return pending;
    }

    public Kind kind() {
        return kind;
    }

    public int[] slots() {
        return slots.clone();
    }

    public int destroyed() {
        return destroyed;
    }

    /** Whether the question is no longer meaningful: the panel is hidden, the trash can is gone or the screen changed. */
    public boolean shouldDiscard(boolean panelVisible, boolean hasTrashCan, Object currentScreen) {
        return pending && (!panelVisible || !hasTrashCan || currentScreen != screen);
    }
}
