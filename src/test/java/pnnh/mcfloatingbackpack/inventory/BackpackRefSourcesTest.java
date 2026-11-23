package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.BackpackRef.Source;

/** 061: the sources only ever grow at the end: their numbers are on the wire. */
class BackpackRefSourcesTest {
    @Test
    void theOldOnesKeepTheirNumbers() {
        Source[] all = Source.values();
        assertEquals(Source.MAIN_HAND, all[0]);
        assertEquals(Source.OFF_HAND, all[1]);
        assertEquals(Source.CHEST, all[2]);
        assertEquals(Source.INVENTORY, all[3]);
        assertEquals(Source.CURIOS, all[4]);
        assertEquals(Source.ACCESSORIES, all[5]);
    }

    @Test
    void theNewOnesComeAfterThemInThisOrder() {
        Source[] all = Source.values();
        assertEquals(10, all.length);
        assertEquals(Source.CURIOS_DEDICATED, all[6]);
        assertEquals(Source.CURIOS_CHARM, all[7]);
        assertEquals(Source.ACCESSORIES_DEDICATED, all[8]);
        assertEquals(Source.ACCESSORIES_CHARM, all[9]);
    }

    @Test
    void everySourceAfterTheHandsAndTheInventoryIsAWornOne() {
        for (Source source : Source.values()) {
            boolean worn = source != Source.MAIN_HAND && source != Source.OFF_HAND && source != Source.INVENTORY;
            assertEquals(worn, source.isWorn(), source.name());
        }
        assertFalse(Source.INVENTORY.isWorn());
        assertTrue(Source.CURIOS_CHARM.isWorn());
    }
}
