package pnnh.mcfloatingbackpack.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MobileToolTest {
    @Test
    void everyToolHasItsOwnByteAndComesBackFromIt() {
        Set<Byte> seen = new HashSet<>();
        for (MobileTool tool : MobileTool.values()) {
            assertEquals(tool, MobileTool.fromByte(tool.id()));
            seen.add(tool.id());
        }
        assertEquals(MobileTool.values().length, seen.size());
    }

    @Test
    void theCraftingTableIsByteZeroAndNeverChanges() {
        assertEquals((byte) 0, MobileTool.CRAFTING_TABLE.id());
        assertEquals(MobileTool.CRAFTING_TABLE, MobileTool.fromByte((byte) 0));
    }

    @Test
    void aByteThatIsNoToolGivesNull() {
        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
            byte value = (byte) b;
            boolean known = false;
            for (MobileTool tool : MobileTool.values()) {
                known |= tool.id() == value;
            }
            if (!known) {
                assertNull(MobileTool.fromByte(value), "byte " + b);
            }
        }
    }
}
