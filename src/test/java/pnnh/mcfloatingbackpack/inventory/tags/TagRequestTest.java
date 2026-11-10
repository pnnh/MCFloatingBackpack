package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.tags.TagRequest.Op;

class TagRequestTest {
    @Test
    void opByteRoundTrip() {
        assertEquals(Op.ADD, Op.fromByte(Op.ADD.toByte()));
        assertEquals(Op.REMOVE, Op.fromByte(Op.REMOVE.toByte()));
        assertNull(Op.fromByte((byte) 2));
        assertNull(Op.fromByte((byte) -1));
    }

    @Test
    void aWellFormedRequestPasses() {
        assertTrue(TagRequest.validate(Op.ADD, new int[] {0, 5, 53}, "wood", 54));
        assertTrue(TagRequest.validate(Op.REMOVE, new int[] {1}, "my_tag-1", 54));
    }

    @Test
    void rejectsABadOperationOrSlotList() {
        assertFalse(TagRequest.validate(null, new int[] {0}, "wood", 54));
        assertFalse(TagRequest.validate(Op.ADD, null, "wood", 54));
        assertFalse(TagRequest.validate(Op.ADD, new int[] {}, "wood", 54));
        assertFalse(TagRequest.validate(Op.ADD, new int[] {1, 1}, "wood", 54));
        assertFalse(TagRequest.validate(Op.ADD, new int[] {-1}, "wood", 54));
        assertFalse(TagRequest.validate(Op.ADD, new int[] {54}, "wood", 54));
    }

    @Test
    void rejectsTooManySlots() {
        int[] all = new int[424];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        assertFalse(TagRequest.validate(Op.ADD, all, "wood", 500));
        int[] ok = new int[423];
        for (int i = 0; i < ok.length; i++) {
            ok[i] = i;
        }
        assertTrue(TagRequest.validate(Op.ADD, ok, "wood", 423));
    }

    @Test
    void rejectsABadTag() {
        for (String tag : new String[] {null, "", "Wood", "a b", "a".repeat(17), "中"}) {
            assertFalse(TagRequest.validate(Op.ADD, new int[] {0}, tag, 54), String.valueOf(tag));
        }
    }
}
