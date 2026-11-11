package pnnh.mcfloatingbackpack.client.panel.tags;

/**
 * 025: what the tag panel's overview is doing, kept for the whole session (not in the backpack): which view is up, which
 * item the detail page shows, the list's filter and scroll, the filter text box and the row that waits for its clear
 * confirmation. The detail page's own text box reuses {@link TagPanelState}'s (018), so switching view drops that state too.
 * Leaving the world clears everything ({@link #reset()}); a new session or the panel being shut by the tool leaving the
 * function area only goes back to the selection view ({@link #resetView()}). The item is kept as its registry id string so
 * this class has no Minecraft types and can be tested.
 */
public final class TagOverviewState {
    /** The selection view is 018's tag panel as it was; the other two are the overview. */
    public enum View {
        SELECTION,
        LIST,
        DETAIL
    }

    private static final TagOverviewState INSTANCE = new TagOverviewState();

    private View view = View.SELECTION;
    private String detailItem;
    private String filter;
    private int listScroll;
    private boolean filterEditing;
    private int filterDropdownIndex = -1;
    private final TagTextInput filterInput = new TagTextInput();
    private final ClearConfirm confirm = new ClearConfirm();

    public static TagOverviewState get() {
        return INSTANCE;
    }

    public View view() {
        return view;
    }

    public String detailItem() {
        return detailItem;
    }

    public void enterList() {
        view = View.LIST;
        detailItem = null;
        leaveTransient();
    }

    public void enterDetail(String itemId) {
        view = View.DETAIL;
        detailItem = itemId;
        leaveTransient();
    }

    /** Back from the detail page; the list keeps its scroll and its filter. */
    public void backToList() {
        enterList();
    }

    public void toSelection() {
        view = View.SELECTION;
        detailItem = null;
        leaveTransient();
    }

    /** Back to the selection view with nothing remembered of the overview. */
    public void resetView() {
        view = View.SELECTION;
        detailItem = null;
        filter = null;
        listScroll = 0;
        leaveTransient();
    }

    /** Forgets everything (leaving the world). */
    public void reset() {
        resetView();
        filterInput.clear();
    }

    /** Drops what belongs to the page being left: a pending clear, the filter box focus, the detail page's text box. */
    private void leaveTransient() {
        TagPanelState tags = TagPanelState.get();
        tags.cancelTransient();
        tags.setScroll(0);
        stopFilterEditing();
        confirm.cancel();
    }

    /** Whether nothing of the overview is remembered, so that resetting it again would change nothing. */
    public boolean isPristine() {
        return view == View.SELECTION && filter == null && listScroll == 0;
    }

    public String filter() {
        return filter;
    }

    public void setFilter(String tag) {
        this.filter = tag == null || tag.isEmpty() ? null : tag;
    }

    public void clearFilter() {
        this.filter = null;
    }

    public int listScroll() {
        return listScroll;
    }

    public void setListScroll(int scroll) {
        this.listScroll = Math.max(0, scroll);
    }

    /** The filter box's buffer: only what is being typed; leaving the box without choosing a tag throws it away. */
    public TagTextInput filterInput() {
        return filterInput;
    }

    public boolean isFilterEditing() {
        return filterEditing;
    }

    /** Puts the focus in the filter box; only the list page has it. */
    public void startFilterEditing() {
        if (view == View.LIST) {
            filterEditing = true;
            filterDropdownIndex = -1;
            confirm.cancel();
        }
    }

    /** Esc, a click elsewhere or leaving the page: whatever was typed but not chosen is dropped, so the box never shows a stale buffer. */
    public void stopFilterEditing() {
        filterEditing = false;
        filterDropdownIndex = -1;
        filterInput.clear();
    }

    public int filterDropdownIndex() {
        return filterDropdownIndex;
    }

    public void setFilterDropdownIndex(int index) {
        this.filterDropdownIndex = index;
    }

    public ClearConfirm confirm() {
        return confirm;
    }
}
