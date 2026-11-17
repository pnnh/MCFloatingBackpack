package pnnh.mcfloatingbackpack.client.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import pnnh.mcfloatingbackpack.snapshot.SnapshotAccess;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;
import pnnh.mcfloatingbackpack.snapshot.VersionPaging;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;
import pnnh.mcfloatingbackpack.snapshot.net.Row;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;
import pnnh.mcfloatingbackpack.snapshot.net.VersionRow;

/**
 * 031: what the snapshot window knows and which of its buttons work. Pure logic: the window draws it and feeds it clicks and
 * the server's replies. {@code P} is whatever the window keeps of a preview (the items), opaque here.
 *
 * <p>Permission is not decided here: {@link #isOp()} is only what the server last said, and it decides which controls exist.
 * While it is false the state cannot hold anything only permission level 2 may use (another scope, a filter, a target, a
 * pending question): the server refuses those whatever the client does, this just keeps the window honest.
 */
public final class SnapshotScreenState<P> {
    /** A question waiting for the player's yes. */
    public enum Pending {
        NONE,
        DELETE_ALL,
        TRIM
    }

    /** The last result line: a code and its plain arguments (see {@link ResultCode}). */
    public record Status(ResultCode code, String a, String b, int n1, int n2) {
    }

    /** A loaded preview of the selected version. */
    public record Preview<P>(long time, int capacity, int slots, P items) {
    }

    private boolean op;
    private Scope scope = Scope.MINE;
    private String ownerFilter = "";
    private int inactiveDays = -1;
    private int page = 1;
    private int pages = 1;
    private int total;
    private int pageSize = 8;
    private List<Row> rows = List.of();

    private UUID selected;
    private List<VersionRow> versions = List.of();
    private int selectedVersion;
    // 057: the versions come in pages and can be limited to one kind (0: all)
    private byte versionKind;
    private int versionPage = 1;
    private int versionPages = 1;
    private int versionTotal;
    private int versionPageSize = 5;
    private Preview<P> preview;
    private ResultCode previewFailure;

    private String target = "";
    private Pending pending = Pending.NONE;
    private int pendingDays;
    private int pendingBackpacks;
    private int pendingFiles;
    private Status status;

    // ---------------------------------------------------------------- what the server says

    /** A list page arrived: it also carries whether the requester has permission level 2. */
    public void applyList(boolean opNow, Scope scopeNow, int pageNo, int pagesNo, int totalNo, List<Row> pageRows) {
        setOp(opNow);
        this.scope = SnapshotAccess.effectiveScope(op, scopeNow);
        this.page = pageNo;
        this.pages = Math.max(1, pagesNo);
        this.total = totalNo;
        this.rows = List.copyOf(pageRows);
    }

    /** The permission changed (or was told for the first time): what only permission level 2 may hold goes when it is lost. */
    public void setOp(boolean opNow) {
        boolean lost = this.op && !opNow;
        this.op = opNow;
        if (!opNow) {
            scope = Scope.MINE;
            ownerFilter = "";
            inactiveDays = -1;
            target = "";
            pending = Pending.NONE;
        }
        if (lost) {
            status = new Status(ResultCode.NO_PERMISSION, "", "", 0, 0);
        }
    }

    /**
     * One page of the versions of the selected backpack arrived; no versions at all (none of any kind) means it is gone (deleted, or never
     * visible). With the list limited to a kind, an empty page only says there is none of that kind.
     */
    public void applyVersions(UUID backpack, byte kind, int page, int pages, int total, List<VersionRow> list) {
        if (selected == null || !selected.equals(backpack)) {
            return;
        }
        if (list.isEmpty() && total == 0 && kind == 0) {
            clearSelection();
            status = new Status(ResultCode.NOT_FOUND, "", "", 0, 0);
            return;
        }
        versionKind = kind;
        versionPage = Math.max(1, page);
        versionPages = Math.max(1, pages);
        versionTotal = Math.max(0, total);
        versions = List.copyOf(list);
        if (selectedVersion > versions.size()) {
            selectedVersion = 0;
            clearPreview();
        }
    }

    /** @return true if the version list is now on another page (the window then asks for it) */
    public boolean gotoVersionPage(int number) {
        if (selected == null || number < 1 || number > versionPages || number == versionPage) {
            return false;
        }
        versionPage = number;
        selectedVersion = 0;
        clearPreview();
        return true;
    }

    /** The kind button: all, timed, change, before an update, all... @return true if there is a backpack to ask about */
    public boolean cycleVersionKind() {
        if (selected == null) {
            return false;
        }
        versionKind = VersionPaging.nextKind(versionKind);
        versionPage = 1;
        selectedVersion = 0;
        clearPreview();
        return true;
    }

    /** How many version rows a page has (what the window asks the server for): at least one. */
    public boolean setVersionPageSize(int size) {
        int fitted = Math.max(1, Math.min(SnapshotRequestRules.MAX_PAGE_SIZE, size));
        if (fitted == versionPageSize) {
            return false;
        }
        versionPageSize = fitted;
        versionPage = 1;
        return true;
    }

    /** A preview arrived; it counts only if it is still the version selected. */
    public void applyPreview(UUID backpack, long time, Preview<P> loaded) {
        if (isSelectedVersion(backpack, time)) {
            preview = loaded;
            previewFailure = null;
        }
    }

    public void applyPreviewFailure(UUID backpack, long time, ResultCode reason) {
        if (isSelectedVersion(backpack, time)) {
            preview = null;
            previewFailure = reason;
        }
    }

    private boolean isSelectedVersion(UUID backpack, long time) {
        Long selectedTime = versionTime();
        return selected != null && selected.equals(backpack) && selectedTime != null && selectedTime == time;
    }

    public void setStatus(Status line) {
        this.status = line;
    }

    // ---------------------------------------------------------------- what the player does

    /** @return true if the selection changed (the window then asks for the versions) */
    public boolean select(UUID backpack) {
        if (backpack == null || backpack.equals(selected)) {
            return false;
        }
        clearSelection();
        selected = backpack;
        return true;
    }

    /** @return true if a version was selected (the window then asks for its preview) */
    public boolean selectVersion(int number) {
        if (number < 1 || number > versions.size() || number == selectedVersion) {
            return false;
        }
        selectedVersion = number;
        clearPreview();
        pending = Pending.NONE;
        return true;
    }

    public boolean gotoPage(int number) {
        if (number < 1 || number > pages || number == page) {
            return false;
        }
        page = number;
        return true;
    }

    /** The window's size changed how many rows fit; @return true if the page size changed (the list is then asked for again from page 1) */
    public boolean setPageSize(int size) {
        int clamped = Math.max(SnapshotRequestRules.MIN_PAGE_SIZE, Math.min(SnapshotRequestRules.MAX_PAGE_SIZE, size));
        if (clamped == pageSize) {
            return false;
        }
        pageSize = clamped;
        page = 1;
        return true;
    }

    public boolean setScope(Scope wanted) {
        if (!op || wanted == scope) {
            return false;
        }
        scope = wanted;
        page = 1;
        clearSelection();
        return true;
    }

    public boolean setOwnerFilter(String name) {
        if (!op || name == null || name.equals(ownerFilter)) {
            return false;
        }
        ownerFilter = name;
        page = 1;
        return true;
    }

    public boolean setInactiveDays(int days) {
        if (!op || days == inactiveDays) {
            return false;
        }
        inactiveDays = days;
        page = 1;
        return true;
    }

    /**
     * The next restore target after the current one: "" (yourself), then the other online players by name. Anyone without
     * permission level 2 has none.
     */
    public boolean cycleTarget(List<String> online, String self) {
        if (!op) {
            return false;
        }
        List<String> choices = new ArrayList<>();
        choices.add("");
        online.stream()
                .filter(name -> !name.equalsIgnoreCase(self))
                .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .forEach(choices::add);
        int at = Math.max(0, choices.indexOf(target));
        target = choices.get((at + 1) % choices.size());
        return true;
    }

    // ---------------------------------------------------------------- which buttons work

    public boolean canRestore() {
        return op && selected != null && selectedVersion != 0;
    }

    public boolean canDeleteOne() {
        return canRestore();
    }

    public boolean canDeleteAll() {
        return op && selected != null && snapshotCount() > 0;
    }

    /** How many snapshots the selected backpack has in all, of every kind (the list's count; the version list may be limited to one kind). */
    private int snapshotCount() {
        for (Row row : rows) {
            if (row.uuid().equals(selected)) {
                return row.versions();
            }
        }
        return Math.max(versionTotal, versions.size());
    }

    /** The clean-up's preview needs a number of days the server will take. */
    public boolean canTrimPreview(int days) {
        return op && SnapshotRequestRules.validTrimDays(days);
    }

    // ---------------------------------------------------------------- questions

    /** Asks "delete all N snapshots of this backpack?" @return false if that cannot be asked now */
    public boolean beginDeleteAll() {
        if (!canDeleteAll()) {
            return false;
        }
        pending = Pending.DELETE_ALL;
        pendingFiles = snapshotCount();
        return true;
    }

    /** The clean-up's preview came back: ask "delete these?" with what it counted. */
    public boolean beginTrim(int days, int backpacks, int files) {
        if (!canTrimPreview(days) || backpacks <= 0) {
            return false;
        }
        pending = Pending.TRIM;
        pendingDays = days;
        pendingBackpacks = backpacks;
        pendingFiles = files;
        return true;
    }

    public void cancelPending() {
        pending = Pending.NONE;
    }

    // ---------------------------------------------------------------- reading

    public boolean isOp() {
        return op;
    }

    public Scope scope() {
        return scope;
    }

    public String ownerFilter() {
        return ownerFilter;
    }

    public int inactiveDays() {
        return inactiveDays;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }

    public int total() {
        return total;
    }

    public int pageSize() {
        return pageSize;
    }

    public List<Row> rows() {
        return rows;
    }

    public UUID selected() {
        return selected;
    }

    public List<VersionRow> versions() {
        return versions;
    }

    public int selectedVersion() {
        return selectedVersion;
    }

    public byte versionKind() {
        return versionKind;
    }

    public int versionPage() {
        return versionPage;
    }

    public int versionPages() {
        return versionPages;
    }

    public int versionTotal() {
        return versionTotal;
    }

    public int versionPageSize() {
        return versionPageSize;
    }

    /** The selected version's time, or null if none is selected. */
    public Long versionTime() {
        return selectedVersion >= 1 && selectedVersion <= versions.size() ? versions.get(selectedVersion - 1).time() : null;
    }

    public Preview<P> preview() {
        return preview;
    }

    public ResultCode previewFailure() {
        return previewFailure;
    }

    public String target() {
        return target;
    }

    public Pending pending() {
        return pending;
    }

    public int pendingDays() {
        return pendingDays;
    }

    public int pendingBackpacks() {
        return pendingBackpacks;
    }

    public int pendingFiles() {
        return pendingFiles;
    }

    public Status status() {
        return status;
    }

    // ---------------------------------------------------------------- helpers

    private void clearSelection() {
        selected = null;
        versions = List.of();
        selectedVersion = 0;
        versionKind = 0;
        versionPage = 1;
        versionPages = 1;
        versionTotal = 0;
        clearPreview();
        pending = Pending.NONE;
    }

    private void clearPreview() {
        preview = null;
        previewFailure = null;
    }
}
