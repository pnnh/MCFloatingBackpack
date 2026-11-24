package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.List;
import java.util.UUID;

/**
 * 065: when the backpack a combined session was looking at is no longer where it was (it was moved in the inventory, dropped, or put away),
 * which member the session goes to. Pure.
 *
 * <p>The same identity found exactly once means it was moved: follow it. Found twice (a copy of the backpack) or not at all: back to the
 * first tab, because there is no telling which is meant. No members: the session ends.
 */
public final class CombinedRecovery {
    public enum Outcome {
        FOLLOWED,
        FIRST,
        END
    }

    /** @param index the member to go to (meaningless for {@link Outcome#END}) */
    public record Result(Outcome outcome, int index) {
    }

    private CombinedRecovery() {
    }

    public static Result recover(List<UUID> memberIdentities, UUID wanted) {
        if (memberIdentities.isEmpty()) {
            return new Result(Outcome.END, -1);
        }
        int found = -1;
        int count = 0;
        if (wanted != null) {
            for (int i = 0; i < memberIdentities.size(); i++) {
                if (wanted.equals(memberIdentities.get(i))) {
                    found = i;
                    count++;
                }
            }
        }
        return count == 1 ? new Result(Outcome.FOLLOWED, found) : new Result(Outcome.FIRST, 0);
    }
}
