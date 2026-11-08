package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.tags.TagOverviewRequest.Op;

/** 025: what the server accepts as a request from the tag overview, before it looks at the backpack. */
class TagOverviewRequestTest {
    @Test
    void opsRoundTripThroughTheirByte() {
        assertEquals(Op.ADD, Op.fromByte((byte) 0));
        assertEquals(Op.REMOVE, Op.fromByte((byte) 1));
        assertEquals(Op.CLEAR, Op.fromByte((byte) 2));
        for (Op op : Op.values()) {
            assertEquals(op, Op.fromByte(op.toByte()));
        }
    }

    @Test
    void unknownOpBytesAreRefused() {
        assertNull(Op.fromByte((byte) -1));
        assertNull(Op.fromByte((byte) 3));
    }

    @Test
    void validRequestsPass() {
        assertTrue(TagOverviewRequest.validate(Op.ADD, "minecraft:stone", "keep"));
        assertTrue(TagOverviewRequest.validate(Op.REMOVE, "minecraft:stone", "trash"));
        assertTrue(TagOverviewRequest.validate(Op.CLEAR, "minecraft:stone", ""));
    }

    @Test
    void missingOpOrItemIsRefused() {
        assertFalse(TagOverviewRequest.validate(null, "minecraft:stone", "a"));
        assertFalse(TagOverviewRequest.validate(Op.ADD, null, "a"));
        assertFalse(TagOverviewRequest.validate(Op.ADD, "", "a"));
        assertFalse(TagOverviewRequest.validate(Op.ADD, "a".repeat(TagOverviewRequest.MAX_ITEM_ID_LENGTH + 1), "a"));
        assertTrue(TagOverviewRequest.validate(Op.ADD, "a".repeat(TagOverviewRequest.MAX_ITEM_ID_LENGTH), "a"));
    }

    @Test
    void addAndRemoveNeedAValidTag() {
        for (Op op : new Op[] {Op.ADD, Op.REMOVE}) {
            assertFalse(TagOverviewRequest.validate(op, "minecraft:stone", null));
            assertFalse(TagOverviewRequest.validate(op, "minecraft:stone", ""));
            assertFalse(TagOverviewRequest.validate(op, "minecraft:stone", "Upper"));
            assertFalse(TagOverviewRequest.validate(op, "minecraft:stone", "has space"));
            assertFalse(TagOverviewRequest.validate(op, "minecraft:stone", "a".repeat(17)));
        }
    }

    @Test
    void clearTakesNoTag() {
        assertFalse(TagOverviewRequest.validate(Op.CLEAR, "minecraft:stone", "trash"));
        assertFalse(TagOverviewRequest.validate(Op.CLEAR, "minecraft:stone", null));
    }
}
