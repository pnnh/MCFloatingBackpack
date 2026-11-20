package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;

class ServerCompatGateTest {
    @AfterEach
    void restoreTheDefaultSource() {
        ServerCompatGate.setLevelSource(null);
    }

    @Test
    void everyActionIsAllowedForEveryOwnerUntilALevelSourceSaysOtherwise() {
        String[] owners = {"minecraft", "sophisticatedbackpacks", "travelersbackpack", "jei", "", null};
        for (Action action : Action.values()) {
            for (String owner : owners) {
                assertTrue(ServerCompatGate.allows(action, owner), action + " / " + owner);
            }
        }
    }

    @Test
    void theFourActionsTheServerEntriesUse() {
        assertTrue(Action.valueOf("DELIVER_TO_CONTAINER") != null);
        assertTrue(Action.valueOf("TAKE_FROM_CONTAINER") != null);
        assertTrue(Action.valueOf("MERGE_CURSOR") != null);
        assertTrue(Action.valueOf("USE_MENU_CURSOR") != null);
    }

    @Test
    void onlyOffRefusesAndItRefusesEveryAction() {
        for (Action action : Action.values()) {
            assertTrue(ServerCompatGate.allows(action, CompatLevel.FULL), action + " FULL");
            assertTrue(ServerCompatGate.allows(action, CompatLevel.GENERIC), action + " GENERIC");
            assertFalse(ServerCompatGate.allows(action, CompatLevel.OFF), action + " OFF");
        }
    }

    @Test
    void theOwnerIsLookedUpInTheLevelSource() {
        ServerCompatGate.setLevelSource(owner -> "somemod".equals(owner) ? CompatLevel.OFF : CompatLevel.FULL);
        for (Action action : Action.values()) {
            assertFalse(ServerCompatGate.allows(action, "somemod"), action.name());
            assertTrue(ServerCompatGate.allows(action, "othermod"), action.name());
            assertTrue(ServerCompatGate.allows(action, "minecraft"), action.name());
        }
    }

    @Test
    void aNullSourceGoesBackToTheDefault() {
        ServerCompatGate.setLevelSource(owner -> CompatLevel.OFF);
        assertFalse(ServerCompatGate.allows(Action.MERGE_CURSOR, "x"));
        ServerCompatGate.setLevelSource(null);
        assertTrue(ServerCompatGate.allows(Action.MERGE_CURSOR, "x"));
    }
}
