package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Filter;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Group;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Page;

/** 030: the snapshot list's filter, order, paging and the trim candidates. */
class SnapshotListingTest {
    private static final long DAY = SnapshotListing.DAY_MS;
    private static final long NOW = 100 * DAY;
    private static final UUID ALICE = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    private static Group group(int n, UUID owner, long time) {
        UUID uuid = new UUID(0, n);
        return new Group(new SnapshotMeta(uuid, owner, "x", 36, time, 3), 2);
    }

    @Test
    void noFilterKeepsEverything() {
        List<Group> all = List.of(group(1, ALICE, NOW), group(2, BOB, NOW - DAY));
        assertEquals(2, SnapshotListing.select(all, Filter.NONE, NOW).size());
    }

    @Test
    void theOwnerFilterKeepsOnlyThatPlayersBackpacks() {
        List<Group> all = List.of(group(1, ALICE, NOW), group(2, BOB, NOW), group(3, ALICE, NOW));
        List<Group> mine = SnapshotListing.select(all, new Filter(ALICE, -1), NOW);
        assertEquals(2, mine.size());
        assertTrue(mine.stream().allMatch(g -> g.newest().owner().equals(ALICE)));
    }

    @Test
    void theInactiveFilterKeepsOnlyBackpacksOlderThanThatManyDays() {
        List<Group> all = List.of(group(1, ALICE, NOW - 40 * DAY), group(2, ALICE, NOW - 30 * DAY), group(3, ALICE, NOW - 10 * DAY));
        List<Group> old = SnapshotListing.select(all, new Filter(null, 30), NOW);
        assertEquals(1, old.size(), "exactly 30 days is not more than 30 days");
        assertEquals(new UUID(0, 1), old.get(0).uuid());
    }

    @Test
    void theNewestComesFirstAndTiesKeepAFixedOrder() {
        List<Group> sorted = SnapshotListing.newestFirst(
                List.of(group(3, ALICE, 10), group(1, ALICE, 30), group(2, ALICE, 10), group(4, ALICE, 20)));
        assertEquals(List.of(new UUID(0, 1), new UUID(0, 4), new UUID(0, 2), new UUID(0, 3)), sorted.stream().map(Group::uuid).toList());
    }

    @Test
    void pagesAreCutFromTheSortedList() {
        List<Group> all = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            all.add(group(i, ALICE, i));
        }
        List<Group> sorted = SnapshotListing.newestFirst(all);
        Page first = SnapshotListing.page(sorted, 1, 10);
        assertEquals(10, first.items().size());
        assertEquals(3, first.pages());
        assertEquals(25, first.total());
        assertTrue(first.inRange());
        assertEquals(5, SnapshotListing.page(sorted, 3, 10).items().size());
        assertFalse(SnapshotListing.page(sorted, 4, 10).inRange());
        assertFalse(SnapshotListing.page(sorted, 0, 10).inRange());
    }

    @Test
    void anEmptyListHasOnePage() {
        Page page = SnapshotListing.page(List.of(), 1, 10);
        assertTrue(page.inRange());
        assertEquals(1, page.pages());
        assertEquals(0, page.total());
    }

    @Test
    void trimChoosesBackpacksUnusedForMoreThanTheDaysAndZeroChoosesAll() {
        List<Group> all = List.of(group(1, ALICE, NOW - 40 * DAY), group(2, ALICE, NOW - 5 * DAY), group(3, BOB, NOW));
        assertEquals(1, SnapshotListing.trimCandidates(all, NOW, 30).size());
        assertEquals(2, SnapshotListing.trimCandidates(all, NOW, 1).size());
        assertEquals(3, SnapshotListing.trimCandidates(all, NOW, 0).size());
    }
}
