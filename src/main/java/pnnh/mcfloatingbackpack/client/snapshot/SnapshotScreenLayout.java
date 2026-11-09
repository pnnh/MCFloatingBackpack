package pnnh.mcfloatingbackpack.client.snapshot;

import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;

/**
 * 031: where the snapshot window's parts go, for a GUI of a given size. Pure arithmetic with no Minecraft types. The window is
 * at most {@value #MAX_W} x {@value #MAX_H}, centred, and never closer than {@value #MARGIN} to an edge of the screen; the list
 * shows as many rows as fit (and asks the server for exactly that many per page), and the preview scrolls when its 9-column grid
 * does not fit, so nothing runs off the screen at any GUI scale.
 */
public final class SnapshotScreenLayout {
    public static final int MARGIN = 8;
    public static final int MAX_W = 400;
    public static final int MAX_H = 260;
    public static final int PAD = 6;
    public static final int TITLE_H = 16;
    public static final int ROW_H = 12;
    public static final int VERSION_ROW_H = 11;
    public static final int VERSION_HEADER_H = 22;
    public static final int MAX_VERSION_ROWS = 5;
    public static final int OP_ROW_H = 20;
    public static final int ACTIONS_H = 20;
    public static final int STATUS_H = 12;
    public static final int GRID_COLS = 9;
    public static final int MAX_SLOT = 18;
    public static final int MIN_SLOT = 8;
    public static final int CLOSE = 10;
    private static final int GAP = PAD;
    private static final int MIN_LIST_W = 90;
    private static final int MAX_LIST_W = 176;

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public boolean isEmpty() {
            return w <= 0 || h <= 0;
        }
    }

    /**
     * @param opRow the scope switch and the two filters (permission level 2 only; empty otherwise)
     * @param actions the restore, delete and clean-up controls (permission level 2 only; empty otherwise)
     * @param listRows how many list rows fit; also the page size asked of the server
     * @param pager the previous / next controls under the list
     * @param versions the selected backpack's header and version rows
     * @param versionRows how many version rows fit
     * @param preview the preview's grid area (its width is the 9 columns' width)
     * @param slot the size of one preview cell
     * @param previewRows how many grid rows are visible at once
     */
    public record Layout(
            Rect dialog,
            Rect close,
            Rect opRow,
            Rect list,
            int listRows,
            Rect pager,
            Rect detail,
            Rect versions,
            int versionRows,
            Rect preview,
            int slot,
            int previewRows,
            Rect actions,
            Rect status) {
    }

    private SnapshotScreenLayout() {
    }

    public static Layout of(int guiWidth, int guiHeight, boolean op) {
        int w = Math.max(0, Math.min(guiWidth - 2 * MARGIN, MAX_W));
        int h = Math.max(0, Math.min(guiHeight - 2 * MARGIN, MAX_H));
        int x = (guiWidth - w) / 2;
        int y = (guiHeight - h) / 2;
        Rect dialog = new Rect(x, y, w, h);
        Rect close = new Rect(x + w - PAD - CLOSE, y + 3, CLOSE, CLOSE);

        int innerX = x + PAD;
        int innerW = Math.max(0, w - 2 * PAD);
        int top = y + TITLE_H;
        int bottom = y + h - PAD;

        Rect status = new Rect(innerX, Math.max(top, bottom - STATUS_H), innerW, Math.min(STATUS_H, Math.max(0, bottom - top)));
        bottom = status.y() - 2;

        Rect actions = new Rect(innerX, bottom, 0, 0);
        Rect opRow = new Rect(innerX, top, 0, 0);
        if (op) {
            actions = new Rect(innerX, Math.max(top, bottom - ACTIONS_H), innerW, Math.min(ACTIONS_H, Math.max(0, bottom - top)));
            bottom = actions.y() - 2;
            opRow = new Rect(innerX, top, innerW, Math.min(OP_ROW_H, Math.max(0, bottom - top)));
            top = opRow.bottom() + 2;
        }
        int contentH = Math.max(0, bottom - top);

        int gridW = GRID_COLS * MAX_SLOT;
        int listW = Math.max(MIN_LIST_W, Math.min(MAX_LIST_W, innerW - GAP - gridW));
        listW = Math.min(listW, innerW);
        int detailW = Math.max(0, innerW - listW - GAP);

        int pagerH = ROW_H + 2;
        Rect list = new Rect(innerX, top, listW, contentH);
        Rect pager = new Rect(innerX, top + Math.max(0, contentH - pagerH), listW, Math.min(pagerH, contentH));
        int listRows = Math.max(1, Math.min(SnapshotRequestRules.MAX_PAGE_SIZE, (contentH - pagerH) / ROW_H));

        Rect detail = new Rect(innerX + listW + GAP, top, detailW, contentH);
        int versionsH = Math.min(VERSION_HEADER_H + MAX_VERSION_ROWS * VERSION_ROW_H, contentH / 2);
        Rect versions = new Rect(detail.x(), top, detailW, versionsH);
        int versionRows = Math.max(0, (versionsH - VERSION_HEADER_H) / VERSION_ROW_H);

        int slot = Math.max(MIN_SLOT, Math.min(MAX_SLOT, detailW / GRID_COLS));
        int previewY = top + versionsH + 2;
        int previewH = Math.max(0, bottom - previewY);
        Rect preview = new Rect(detail.x(), previewY, Math.min(detailW, GRID_COLS * slot), previewH);
        int previewRows = previewH / slot;
        return new Layout(dialog, close, opRow, list, listRows, pager, detail, versions, versionRows, preview, slot, previewRows, actions, status);
    }

    /** How many grid rows a preview of {@code slots} cells has (9 to a row). */
    public static int gridRows(int slots) {
        return (slots + GRID_COLS - 1) / GRID_COLS;
    }

    /** The furthest the grid can be scrolled: how many rows are hidden below the visible ones. */
    public static int maxScroll(int slots, int visibleRows) {
        return Math.max(0, gridRows(slots) - Math.max(1, visibleRows));
    }
}
