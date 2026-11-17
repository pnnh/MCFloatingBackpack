package pnnh.mcfloatingbackpack.client.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenState.Pending;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenState.Preview;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;
import pnnh.mcfloatingbackpack.snapshot.net.Row;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;
import pnnh.mcfloatingbackpack.snapshot.net.VersionRow;

/** 031: the window's state: what it may hold for whom, which buttons work, how questions are asked. */
class SnapshotScreenStateTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    private static Row row(UUID uuid) {
        return new Row(uuid, new UUID(9, 9), "Dev", 36, 1000L, 3, 2);
    }

    private static List<VersionRow> versions(int n) {
        List<VersionRow> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new VersionRow(5000L - i * 100L, 3 + i, "Dev", 36, (byte) 2, ""));
        }
        return list;
    }

    private static SnapshotScreenState<String> player() {
        SnapshotScreenState<String> state = new SnapshotScreenState<>();
        state.applyList(false, Scope.MINE, 1, 1, 2, List.of(row(A), row(B)));
        return state;
    }

    private static SnapshotScreenState<String> op() {
        SnapshotScreenState<String> state = new SnapshotScreenState<>();
        state.applyList(true, Scope.MINE, 1, 1, 2, List.of(row(A), row(B)));
        return state;
    }

    private static SnapshotScreenState<String> opWithVersionSelected() {
        SnapshotScreenState<String> state = op();
        state.select(A);
        state.applyVersions(A, (byte) 0, 1, 1, 3, versions(3));
        state.selectVersion(2);
        return state;
    }

    @Test
    void aPlayerWithoutPermissionHasNothingOnlyAnOpMayDo() {
        SnapshotScreenState<String> state = player();
        assertFalse(state.isOp());
        assertEquals(Scope.MINE, state.scope());
        assertFalse(state.setScope(Scope.ALL));
        assertFalse(state.setOwnerFilter("Bob"));
        assertFalse(state.setInactiveDays(30));
        assertFalse(state.cycleTarget(List.of("Bob"), "Dev"));
        state.select(A);
        state.applyVersions(A, (byte) 0, 1, 1, 2, versions(2));
        state.selectVersion(1);
        assertFalse(state.canRestore());
        assertFalse(state.canDeleteOne());
        assertFalse(state.canDeleteAll());
        assertFalse(state.canTrimPreview(30));
        assertFalse(state.beginDeleteAll());
        assertFalse(state.beginTrim(30, 2, 4));
        assertEquals(Pending.NONE, state.pending());
        assertEquals("", state.ownerFilter());
        assertEquals(-1, state.inactiveDays());
        assertEquals("", state.target());
    }

    @Test
    void theServerCannotMakeAPlayerWithoutPermissionHoldAnotherScope() {
        SnapshotScreenState<String> state = new SnapshotScreenState<>();
        state.applyList(false, Scope.ALL, 1, 1, 0, List.of());
        assertEquals(Scope.MINE, state.scope());
    }

    @Test
    void anOpCanUseTheOpControlsOnceSomethingIsSelected() {
        SnapshotScreenState<String> state = op();
        assertFalse(state.canRestore(), "nothing selected");
        assertFalse(state.canDeleteAll());
        assertTrue(state.canTrimPreview(0));
        assertFalse(state.canTrimPreview(-1));
        state.select(A);
        assertTrue(state.canDeleteAll(), "the list already said the backpack has snapshots");
        state.applyVersions(A, (byte) 0, 1, 1, 3, versions(3));
        assertTrue(state.canDeleteAll());
        assertFalse(state.canRestore(), "no version selected");
        assertTrue(state.selectVersion(2));
        assertTrue(state.canRestore());
        assertTrue(state.canDeleteOne());
    }

    @Test
    void losingPermissionTakesEverythingOnlyAnOpHoldsAway() {
        SnapshotScreenState<String> state = op();
        state.setScope(Scope.ALL);
        state.setOwnerFilter("Bob");
        state.setInactiveDays(30);
        state.cycleTarget(List.of("Bob"), "Dev");
        state.select(A);
        state.applyVersions(A, (byte) 0, 1, 1, 3, versions(3));
        state.selectVersion(2);
        assertTrue(state.beginDeleteAll());
        state.applyList(false, Scope.ALL, 1, 1, 0, List.of());
        assertFalse(state.isOp());
        assertEquals(Scope.MINE, state.scope());
        assertEquals("", state.ownerFilter());
        assertEquals(-1, state.inactiveDays());
        assertEquals("", state.target());
        assertEquals(Pending.NONE, state.pending());
        assertFalse(state.canRestore());
        assertEquals(ResultCode.NO_PERMISSION, state.status().code());
    }

    @Test
    void selectingAnotherBackpackClearsTheVersionsThePreviewAndTheQuestion() {
        SnapshotScreenState<String> state = opWithVersionSelected();
        state.applyPreview(A, state.versionTime(), new Preview<>(state.versionTime(), 36, 4, "items"));
        assertTrue(state.beginDeleteAll());
        assertTrue(state.select(B));
        assertEquals(B, state.selected());
        assertTrue(state.versions().isEmpty());
        assertEquals(0, state.selectedVersion());
        assertNull(state.preview());
        assertEquals(Pending.NONE, state.pending());
        assertFalse(state.select(B), "the same backpack again changes nothing");
    }

    @Test
    void aVersionListForAnotherBackpackIsIgnored() {
        SnapshotScreenState<String> state = op();
        state.select(A);
        state.applyVersions(B, (byte) 0, 1, 1, 2, versions(2));
        assertTrue(state.versions().isEmpty());
    }

    @Test
    void anEmptyVersionListMeansTheBackpackIsGone() {
        SnapshotScreenState<String> state = opWithVersionSelected();
        state.applyVersions(A, (byte) 0, 1, 1, 0, List.of());
        assertNull(state.selected());
        assertEquals(0, state.selectedVersion());
        assertEquals(ResultCode.NOT_FOUND, state.status().code());
    }

    @Test
    void aPreviewCountsOnlyIfItIsStillTheVersionSelected() {
        SnapshotScreenState<String> state = opWithVersionSelected();
        long time = state.versionTime();
        state.applyPreview(A, time + 1, new Preview<>(time + 1, 36, 1, "other"));
        assertNull(state.preview());
        state.applyPreview(B, time, new Preview<>(time, 36, 1, "other"));
        assertNull(state.preview());
        state.applyPreview(A, time, new Preview<>(time, 36, 1, "mine"));
        assertEquals("mine", state.preview().items());
        state.selectVersion(1);
        assertNull(state.preview(), "a new version has no preview until it arrives");
        long first = state.versionTime();
        state.applyPreviewFailure(A, first, ResultCode.TOO_LARGE);
        assertEquals(ResultCode.TOO_LARGE, state.previewFailure());
    }

    @Test
    void aShorterVersionListDropsASelectionThatIsNoLongerThere() {
        SnapshotScreenState<String> state = opWithVersionSelected();
        state.applyVersions(A, (byte) 0, 1, 1, 1, versions(1));
        assertEquals(0, state.selectedVersion());
        assertNull(state.preview());
    }

    @Test
    void deletingAllAsksFirstAndCancelLeavesNothing() {
        SnapshotScreenState<String> state = opWithVersionSelected();
        assertTrue(state.beginDeleteAll());
        assertEquals(Pending.DELETE_ALL, state.pending());
        assertEquals(2, state.pendingFiles(), "what the list said: every snapshot of the backpack");
        state.cancelPending();
        assertEquals(Pending.NONE, state.pending());
    }

    @Test
    void aCleanUpIsAskedAboutOnlyWithSomethingToDelete() {
        SnapshotScreenState<String> state = op();
        assertFalse(state.beginTrim(30, 0, 0));
        assertTrue(state.beginTrim(30, 3, 9));
        assertEquals(Pending.TRIM, state.pending());
        assertEquals(30, state.pendingDays());
        assertEquals(3, state.pendingBackpacks());
        assertEquals(9, state.pendingFiles());
        assertFalse(state.beginTrim(-1, 3, 9));
    }

    @Test
    void selectingAVersionCancelsAQuestion() {
        SnapshotScreenState<String> state = opWithVersionSelected();
        state.beginDeleteAll();
        state.selectVersion(1);
        assertEquals(Pending.NONE, state.pending());
    }

    @Test
    void pagesAreWithinTheRangeAndChangingTheScopeOrFilterGoesBackToPageOne() {
        SnapshotScreenState<String> state = new SnapshotScreenState<>();
        state.applyList(true, Scope.MINE, 1, 3, 25, List.of(row(A)));
        assertFalse(state.gotoPage(0));
        assertFalse(state.gotoPage(4));
        assertTrue(state.gotoPage(3));
        assertEquals(3, state.page());
        assertTrue(state.setScope(Scope.ALL));
        assertEquals(1, state.page());
        state.gotoPage(2);
        assertTrue(state.setOwnerFilter("Bob"));
        assertEquals(1, state.page());
        state.gotoPage(2);
        assertTrue(state.setInactiveDays(30));
        assertEquals(1, state.page());
        assertFalse(state.setInactiveDays(30));
    }

    @Test
    void thePageSizeIsClampedAndAChangeGoesBackToPageOne() {
        SnapshotScreenState<String> state = new SnapshotScreenState<>();
        state.applyList(false, Scope.MINE, 2, 3, 20, List.of());
        assertFalse(state.setPageSize(8), "8 is what it starts as");
        assertTrue(state.setPageSize(5));
        assertEquals(5, state.pageSize());
        assertEquals(1, state.page());
        state.setPageSize(500);
        assertEquals(16, state.pageSize());
        state.setPageSize(-3);
        assertEquals(1, state.pageSize());
    }

    @Test
    void theRestoreTargetCyclesThroughYourselfAndTheOthersInAlphabeticalOrder() {
        SnapshotScreenState<String> state = op();
        List<String> online = List.of("zed", "Dev", "Alice", "bob");
        assertEquals("", state.target());
        state.cycleTarget(online, "Dev");
        assertEquals("Alice", state.target());
        state.cycleTarget(online, "Dev");
        assertEquals("bob", state.target());
        state.cycleTarget(online, "Dev");
        assertEquals("zed", state.target());
        state.cycleTarget(online, "Dev");
        assertEquals("", state.target(), "and back to yourself");
    }

    @Test
    void aTargetWhoLeftStartsTheCycleOverFromTheNextOne() {
        SnapshotScreenState<String> state = op();
        state.cycleTarget(List.of("Alice", "bob"), "Dev");
        assertEquals("Alice", state.target());
        state.cycleTarget(List.of("bob"), "Dev");
        assertEquals("bob", state.target());
    }

    // ---- 057: the versions come in pages and can be limited to a kind

    @Test
    void aPageOfVersionsRemembersItsPageKindAndTotal() {
        SnapshotScreenState<String> state = op();
        state.select(A);
        state.applyVersions(A, (byte) 1, 2, 4, 17, versions(5));
        assertEquals(1, state.versionKind());
        assertEquals(2, state.versionPage());
        assertEquals(4, state.versionPages());
        assertEquals(17, state.versionTotal());
        assertEquals(5, state.versions().size());
    }

    @Test
    void theKindButtonGoesAroundAndBackToTheFirstPageAndClearsTheSelection() {
        SnapshotScreenState<String> state = op();
        state.select(A);
        state.applyVersions(A, (byte) 0, 2, 3, 12, versions(5));
        state.selectVersion(2);
        assertTrue(state.cycleVersionKind());
        assertEquals(1, state.versionKind());
        assertEquals(1, state.versionPage());
        assertEquals(0, state.selectedVersion());
        state.cycleVersionKind();
        state.cycleVersionKind();
        assertEquals(3, state.versionKind());
        state.cycleVersionKind();
        assertEquals(0, state.versionKind());
    }

    @Test
    void theKindButtonNeedsABackpack() {
        SnapshotScreenState<String> state = op();
        assertFalse(state.cycleVersionKind());
        assertFalse(state.gotoVersionPage(2));
    }

    @Test
    void versionPagesStayWithinTheRangeAndChangingPageClearsTheSelection() {
        SnapshotScreenState<String> state = op();
        state.select(A);
        state.applyVersions(A, (byte) 0, 1, 3, 12, versions(5));
        state.selectVersion(1);
        assertFalse(state.gotoVersionPage(0));
        assertFalse(state.gotoVersionPage(4));
        assertFalse(state.gotoVersionPage(1), "already there");
        assertTrue(state.gotoVersionPage(3));
        assertEquals(3, state.versionPage());
        assertEquals(0, state.selectedVersion());
    }

    @Test
    void anEmptyPageOfOneKindOnlyMeansThereIsNoneOfThatKind() {
        SnapshotScreenState<String> state = op();
        state.select(A);
        state.applyVersions(A, (byte) 3, 1, 1, 0, List.of());
        assertEquals(A, state.selected(), "the backpack is not gone");
        assertTrue(state.versions().isEmpty());
    }

    @Test
    void deletingAllCountsEverySnapshotOfTheBackpackNotJustTheKindShown() {
        SnapshotScreenState<String> state = new SnapshotScreenState<>();
        state.applyList(true, Scope.MINE, 1, 1, 1, List.of(new Row(A, A, "Dev", 36, 5000L, 3, 85)));
        state.select(A);
        state.applyVersions(A, (byte) 3, 1, 1, 2, versions(2));
        assertTrue(state.canDeleteAll());
        assertTrue(state.beginDeleteAll());
        assertEquals(85, state.pendingFiles());
    }

    @Test
    void theVersionPageSizeIsAtLeastOneAndAtMostTheProtocolsPageSize() {
        SnapshotScreenState<String> state = op();
        assertFalse(state.setVersionPageSize(5), "five is the default");
        assertTrue(state.setVersionPageSize(0));
        assertEquals(1, state.versionPageSize());
        assertTrue(state.setVersionPageSize(500));
        assertEquals(16, state.versionPageSize());
    }
}
