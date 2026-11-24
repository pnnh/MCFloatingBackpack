package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.combined.CombinedRecovery.Outcome;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedRecovery.Result;

/** 065: where a combined session goes when its backpack has moved or gone. */
class CombinedRecoveryTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    @Test
    void aBackpackFoundOnceIsFollowed() {
        Result result = CombinedRecovery.recover(List.of(A, B, C), C);
        assertEquals(Outcome.FOLLOWED, result.outcome());
        assertEquals(2, result.index());
    }

    @Test
    void aBackpackThatIsGoneGoesToTheFirstTab() {
        Result result = CombinedRecovery.recover(List.of(A, B), C);
        assertEquals(Outcome.FIRST, result.outcome());
        assertEquals(0, result.index());
    }

    @Test
    void aCopyFoundTwiceGoesToTheFirstTab() {
        Result result = CombinedRecovery.recover(List.of(A, B, B), B);
        assertEquals(Outcome.FIRST, result.outcome());
        assertEquals(0, result.index());
    }

    @Test
    void noMembersEndTheSession() {
        assertEquals(Outcome.END, CombinedRecovery.recover(List.of(), A).outcome());
    }

    @Test
    void membersWithoutIdentityAreNeverMatched() {
        List<UUID> members = new ArrayList<>();
        members.add(null);
        members.add(A);
        assertEquals(Outcome.FOLLOWED, CombinedRecovery.recover(members, A).outcome());
        assertEquals(Outcome.FIRST, CombinedRecovery.recover(members, null).outcome());
    }
}
