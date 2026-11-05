package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;

class ServerCompatGateTest {
    @Test
    void everyActionIsAllowedForEveryOwnerForNow() {
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
}
