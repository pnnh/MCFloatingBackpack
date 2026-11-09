package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Group;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;

/** 031: who may see which backpack's snapshots. */
class SnapshotAccessTest {
    private static final UUID ALICE = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    private static Group group(int n, UUID owner) {
        return new Group(new SnapshotMeta(new UUID(0, n), owner, "x", 36, 1000L, 3), 1);
    }

    @Test
    void permissionLevelTwoIsWhatTheCommandsAskFor() {
        assertEquals(2, SnapshotAccess.OP_LEVEL);
    }

    @Test
    void aPlayerSeesTheirOwnBackpacksAndAnOpSeesAll() {
        assertTrue(SnapshotAccess.canSee(false, ALICE, ALICE));
        assertFalse(SnapshotAccess.canSee(false, ALICE, BOB));
        assertTrue(SnapshotAccess.canSee(true, ALICE, BOB));
        assertTrue(SnapshotAccess.canSee(true, null, BOB), "the console has no id and sees all");
        assertFalse(SnapshotAccess.canSee(false, null, BOB), "no id and no permission sees nothing");
    }

    @Test
    void visibleKeepsOnlyWhatTheRequesterMaySee() {
        List<Group> all = List.of(group(1, ALICE), group(2, BOB), group(3, ALICE));
        assertEquals(2, SnapshotAccess.visible(all, false, ALICE).size());
        assertEquals(1, SnapshotAccess.visible(all, false, BOB).size());
        assertEquals(3, SnapshotAccess.visible(all, true, ALICE).size());
        assertTrue(SnapshotAccess.visible(all, false, UUID.randomUUID()).isEmpty());
    }

    @Test
    void anyoneWithoutPermissionGetsTheirOwnScopeWhateverTheyAskFor() {
        assertEquals(Scope.MINE, SnapshotAccess.effectiveScope(false, Scope.ALL));
        assertEquals(Scope.MINE, SnapshotAccess.effectiveScope(false, Scope.MINE));
        assertEquals(Scope.ALL, SnapshotAccess.effectiveScope(true, Scope.ALL));
        assertEquals(Scope.MINE, SnapshotAccess.effectiveScope(true, Scope.MINE));
    }

    @Test
    void onlyPermissionLevelTwoMayFilter() {
        assertTrue(SnapshotAccess.mayFilter(true));
        assertFalse(SnapshotAccess.mayFilter(false));
    }
}
