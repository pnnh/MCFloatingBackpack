package pnnh.mcfloatingbackpack.inventory.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklistRequest.Op;

/** 024: what the server accepts as a request to change the blacklist. */
class PickupBlacklistRequestTest {
    @Test
    void opsRoundTripThroughTheirByte() {
        assertEquals(Op.ADD, Op.fromByte((byte) 0));
        assertEquals(Op.REMOVE, Op.fromByte((byte) 1));
        assertEquals(0, Op.ADD.toByte());
        assertEquals(1, Op.REMOVE.toByte());
    }

    @Test
    void unknownOpBytesAreRefused() {
        assertNull(Op.fromByte((byte) 2));
        assertNull(Op.fromByte((byte) -1));
    }

    @Test
    void validRequestPasses() {
        assertTrue(PickupBlacklistRequest.validate(Op.ADD, "stone"));
        assertTrue(PickupBlacklistRequest.validate(Op.REMOVE, "trash"));
    }

    @Test
    void missingOpOrBadTagIsRefused() {
        assertFalse(PickupBlacklistRequest.validate(null, "stone"));
        assertFalse(PickupBlacklistRequest.validate(Op.ADD, null));
        assertFalse(PickupBlacklistRequest.validate(Op.ADD, ""));
        assertFalse(PickupBlacklistRequest.validate(Op.ADD, "has space"));
        assertFalse(PickupBlacklistRequest.validate(Op.ADD, "Upper"));
        assertFalse(PickupBlacklistRequest.validate(Op.REMOVE, "a".repeat(17)));
    }
}
