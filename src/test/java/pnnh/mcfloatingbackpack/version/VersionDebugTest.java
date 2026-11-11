package pnnh.mcfloatingbackpack.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 041: the two switches of the world guard. */
class VersionDebugTest {
    private static final String ACCEPT = "mcfloatingbackpack.acceptDowngrade";
    private static final String MARKER = "mcfloatingbackpack.debug.worldMarkerRevision";

    @AfterEach
    void clear() {
        System.clearProperty(ACCEPT);
        System.clearProperty(MARKER);
    }

    @Test
    void acceptDowngradeIsOffUnlessGiven() {
        assertFalse(VersionDebug.acceptDowngrade());
        System.setProperty(ACCEPT, "false");
        assertFalse(VersionDebug.acceptDowngrade());
        System.setProperty(ACCEPT, "yes please");
        assertFalse(VersionDebug.acceptDowngrade());
        System.setProperty(ACCEPT, "true");
        assertTrue(VersionDebug.acceptDowngrade());
    }

    @Test
    void acceptDowngradeIsNotADebugSwitch() {
        System.setProperty("mcfloatingbackpack.debug.acceptDowngrade", "true");
        try {
            assertFalse(VersionDebug.acceptDowngrade());
        } finally {
            System.clearProperty("mcfloatingbackpack.debug.acceptDowngrade");
        }
    }

    @Test
    void theWorldMarkerRevisionIsAnIntegerOrNothing() {
        assertNull(VersionDebug.worldMarkerRevisionOverride());
        System.setProperty(MARKER, " 99 ");
        assertEquals(99, VersionDebug.worldMarkerRevisionOverride());
        System.setProperty(MARKER, "lots");
        assertNull(VersionDebug.worldMarkerRevisionOverride());
        System.setProperty(MARKER, "");
        assertNull(VersionDebug.worldMarkerRevisionOverride());
    }
}
