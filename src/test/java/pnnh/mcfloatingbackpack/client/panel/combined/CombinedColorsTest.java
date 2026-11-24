package pnnh.mcfloatingbackpack.client.panel.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 065: the tab colours are opaque, all different, and follow the position. */
class CombinedColorsTest {
    @Test
    void sixteenDistinctOpaqueColours() {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < CombinedColors.count(); i++) {
            int argb = CombinedColors.argb(i);
            assertEquals(0xFF, argb >>> 24, "opaque " + i);
            seen.add(argb);
        }
        assertEquals(16, CombinedColors.count());
        assertEquals(16, seen.size());
    }

    @Test
    void positionsWrapRound() {
        assertEquals(CombinedColors.argb(0), CombinedColors.argb(16));
        assertNotEquals(CombinedColors.argb(0), CombinedColors.argb(1));
        assertEquals(CombinedColors.argb(15), CombinedColors.argb(-1));
    }
}
