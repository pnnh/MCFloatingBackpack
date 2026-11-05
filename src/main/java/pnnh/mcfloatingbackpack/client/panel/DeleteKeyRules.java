package pnnh.mcfloatingbackpack.client.panel;

/**
 * 022: what the delete key does. The key is only taken (the event cancelled) when this names a target; with
 * {@link Target#NONE} the caller leaves the key alone, so the screen, JEI's search box or the game see it as if the mod
 * were not there. Pure logic with no Minecraft types, so it can be unit-tested.
 */
public final class DeleteKeyRules {
    /** What the current screen is, as far as the key is concerned. */
    public enum ScreenKind {
        /** The panel's own transparent host screen (019). */
        HOST,
        /** The vanilla player inventory screen. */
        INVENTORY,
        /** A vanilla chest-menu screen (chest, large chest, barrel, ender chest). */
        CHEST,
        /** Any other screen the panel sits on. Its main area still works; its slots are none of our business. */
        OTHER
    }

    public enum Target {
        NONE,
        /** The selected main-area slots. */
        MAIN_SELECTION,
        /** The main-area slot under the mouse. */
        MAIN_HOVER,
        /** The slot under the mouse on the vanilla inventory or chest screen. */
        MENU_SLOT
    }

    /**
     * @param tagPanelOpen the tag panel covers the main area, which is then not operable
     * @param tagEditing the tag text box has the focus
     * @param selectionHasItem the main-area selection holds at least one slot with an item in it
     * @param hoverMainHasItem the mouse is over a main-area slot that holds an item
     * @param hoverMenuAllowedWithItem the mouse is over a slot of the screen that the key may take and that holds an item
     */
    public record Inputs(
            boolean panelVisible,
            boolean hasTrashCan,
            boolean confirmPending,
            boolean tagPanelOpen,
            boolean tagEditing,
            ScreenKind kind,
            boolean selectionHasItem,
            boolean hoverMainHasItem,
            boolean hoverMenuAllowedWithItem) {
    }

    private DeleteKeyRules() {
    }

    public static Target decide(Inputs in) {
        if (!in.panelVisible() || !in.hasTrashCan() || in.confirmPending() || in.tagPanelOpen() || in.tagEditing()) {
            return Target.NONE;
        }
        if (in.selectionHasItem()) {
            return Target.MAIN_SELECTION;
        }
        if (in.hoverMainHasItem()) {
            return Target.MAIN_HOVER;
        }
        boolean menuScreen = in.kind() == ScreenKind.INVENTORY || in.kind() == ScreenKind.CHEST;
        if (menuScreen && in.hoverMenuAllowedWithItem()) {
            return Target.MENU_SLOT;
        }
        return Target.NONE;
    }
}
