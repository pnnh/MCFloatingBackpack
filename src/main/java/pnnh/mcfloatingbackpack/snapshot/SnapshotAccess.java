package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Group;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;

/**
 * 031: who may see which backpack's snapshots. The one rule the commands (030) and the window (031) share: permission level 2
 * sees everybody's, anybody else sees only the backpacks they used last, and everything else looks, to them, as if it did not
 * exist. Pure logic.
 */
public final class SnapshotAccess {
    /** The permission level the commands ask for ({@code hasPermission(2)}) and the window asks for too. */
    public static final int OP_LEVEL = 2;

    private SnapshotAccess() {
    }

    /** Whether this backpack's snapshots may be seen by the requester. */
    public static boolean canSee(boolean op, UUID self, UUID owner) {
        return op || (self != null && self.equals(owner));
    }

    /** What the requester may see of {@code groups}. */
    public static List<Group> visible(Collection<Group> groups, boolean op, UUID self) {
        if (op) {
            return new ArrayList<>(groups);
        }
        List<Group> own = new ArrayList<>();
        for (Group group : groups) {
            if (canSee(false, self, group.newest().owner())) {
                own.add(group);
            }
        }
        return own;
    }

    /** The scope a request really gets: someone without permission level 2 always gets their own, whatever they asked for. */
    public static Scope effectiveScope(boolean op, Scope requested) {
        return op && requested == Scope.ALL ? Scope.ALL : Scope.MINE;
    }

    /** Whether the owner and inactive-days filters of a list request count: they are for permission level 2 only. */
    public static boolean mayFilter(boolean op) {
        return op;
    }
}
