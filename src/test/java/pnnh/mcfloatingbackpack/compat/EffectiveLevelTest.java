package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 059: the stricter of the server's and the client's level. */
class EffectiveLevelTest {
    private static CompatPolicy policy(CompatLevel level) {
        return level == CompatLevel.FULL ? CompatPolicy.DEFAULT : CompatPolicy.parse(List.of("somemod=" + level)).policy();
    }

    @Test
    void allNineCombinationsGiveTheStricter() {
        for (CompatLevel server : CompatLevel.values()) {
            for (CompatLevel client : CompatLevel.values()) {
                CompatLevel expected = server.ordinal() >= client.ordinal() ? server : client;
                assertEquals(expected, EffectiveLevel.effective(policy(server), policy(client), "somemod"), server + " / " + client);
            }
        }
    }

    @Test
    void theTableOfTheContract() {
        assertEquals(CompatLevel.FULL, EffectiveLevel.stricter(CompatLevel.FULL, CompatLevel.FULL));
        assertEquals(CompatLevel.GENERIC, EffectiveLevel.stricter(CompatLevel.FULL, CompatLevel.GENERIC));
        assertEquals(CompatLevel.GENERIC, EffectiveLevel.stricter(CompatLevel.GENERIC, CompatLevel.FULL));
        assertEquals(CompatLevel.OFF, EffectiveLevel.stricter(CompatLevel.GENERIC, CompatLevel.OFF));
        assertEquals(CompatLevel.OFF, EffectiveLevel.stricter(CompatLevel.OFF, CompatLevel.FULL));
        assertEquals(CompatLevel.OFF, EffectiveLevel.stricter(CompatLevel.OFF, CompatLevel.OFF));
    }

    @Test
    void aMissingLevelCountsAsFull() {
        assertEquals(CompatLevel.FULL, EffectiveLevel.stricter(null, null));
        assertEquals(CompatLevel.OFF, EffectiveLevel.stricter(null, CompatLevel.OFF));
        assertEquals(CompatLevel.GENERIC, EffectiveLevel.effective(null, policy(CompatLevel.GENERIC), "somemod"));
        assertEquals(CompatLevel.FULL, EffectiveLevel.effective(null, null, "somemod"));
    }

    @Test
    void onADedicatedServerTheClientConfigIsTheDefaultAndChangesNothing() {
        for (CompatLevel server : CompatLevel.values()) {
            assertEquals(server, EffectiveLevel.effective(policy(server), CompatPolicy.DEFAULT, "somemod"));
        }
    }

    @Test
    void withNothingSetOnTheServerTheClientsLevelCounts() {
        for (CompatLevel client : CompatLevel.values()) {
            assertEquals(client, EffectiveLevel.effective(CompatPolicy.DEFAULT, policy(client), "somemod"));
        }
    }

    @Test
    void vanillaAndAnotherModAreNotAffected() {
        CompatPolicy off = policy(CompatLevel.OFF);
        assertEquals(CompatLevel.FULL, EffectiveLevel.effective(off, off, "minecraft"));
        assertEquals(CompatLevel.FULL, EffectiveLevel.effective(off, off, ""));
        assertEquals(CompatLevel.FULL, EffectiveLevel.effective(off, off, null));
        assertEquals(CompatLevel.FULL, EffectiveLevel.effective(off, off, "othermod"));
    }
}
