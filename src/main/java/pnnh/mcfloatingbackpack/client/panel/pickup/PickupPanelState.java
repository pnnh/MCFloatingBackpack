package pnnh.mcfloatingbackpack.client.panel.pickup;

import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelState;
import pnnh.mcfloatingbackpack.client.panel.tags.TagTextInput;

/**
 * 024: what the pickup blacklist page is doing, kept for the whole session (not in the backpack). Same shape as
 * {@link TagPanelState}: collapsing keeps the typed text and the scroll position, so reopening finds the page as it was
 * left; leaving the world clears everything. The page and the tag panel both cover the main area, so expanding one
 * collapses the other. No Minecraft types, so it can be tested.
 */
public final class PickupPanelState {
    private static final PickupPanelState INSTANCE = new PickupPanelState();

    private boolean open;
    private boolean editing;
    private int dropdownIndex = -1;
    private String pendingDelete;
    private int scroll;
    private final TagTextInput input = new TagTextInput();

    public static PickupPanelState get() {
        return INSTANCE;
    }

    /** The text box's buffer; it survives collapsing the page and is cleared when leaving the world. */
    public TagTextInput input() {
        return input;
    }

    public boolean isOpen() {
        return open;
    }

    /** Expands a collapsed page (collapsing the tag panel, which covers the same area), collapses an expanded one. */
    public void toggle() {
        if (open) {
            collapse();
        } else {
            TagPanelState.get().collapse();
            open = true;
        }
    }

    /** Collapses the page and ends editing; the text and the scroll position stay. */
    public void collapse() {
        open = false;
        cancelTransient();
    }

    /** Forgets everything (leaving the world). */
    public void reset() {
        open = false;
        scroll = 0;
        input.clear();
        cancelTransient();
    }

    public boolean isEditing() {
        return editing;
    }

    /** Puts the focus in the text box; only an expanded page can have it. */
    public void startEditing() {
        if (open) {
            editing = true;
            dropdownIndex = -1;
            pendingDelete = null;
        }
    }

    public void stopEditing() {
        editing = false;
        dropdownIndex = -1;
    }

    /** Ends editing and drops a pending delete, without touching the scroll position or the text. */
    public void cancelTransient() {
        editing = false;
        dropdownIndex = -1;
        pendingDelete = null;
    }

    public int dropdownIndex() {
        return dropdownIndex;
    }

    public void setDropdownIndex(int index) {
        this.dropdownIndex = index;
    }

    /** The tag showing a delete mark, or null. */
    public String pendingDelete() {
        return pendingDelete;
    }

    public void setPendingDelete(String tag) {
        this.pendingDelete = tag;
    }

    public int scroll() {
        return scroll;
    }

    public void setScroll(int scroll) {
        this.scroll = Math.max(0, scroll);
    }
}
