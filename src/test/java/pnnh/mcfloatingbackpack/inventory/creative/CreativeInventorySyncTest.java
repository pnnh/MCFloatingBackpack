package pnnh.mcfloatingbackpack.inventory.creative;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 029: when the server sends a creative player the whole inventory again after a quick transfer. */
class CreativeInventorySyncTest {
    @Test
    void onlyACreativePlayerWithTheirOwnInventoryOpenGetsIt() {
        assertTrue(CreativeInventorySync.shouldSync(true, true));
        assertFalse(CreativeInventorySync.shouldSync(false, true));
        assertFalse(CreativeInventorySync.shouldSync(true, false));
        assertFalse(CreativeInventorySync.shouldSync(false, false));
    }
}
