package pnnh.mcfloatingbackpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModIdTest {
    @Test
    void modIdMatchesNeoForgeRules() {
        assertEquals("mcfloatingbackpack", MCFloatingBackpack.MOD_ID);
        assertTrue(MCFloatingBackpack.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"));
    }
}
