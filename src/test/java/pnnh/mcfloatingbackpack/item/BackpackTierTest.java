package pnnh.mcfloatingbackpack.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BackpackTierTest {
    @Test
    void capacitiesMatchSpec() {
        assertEquals(36, BackpackTier.LEATHER.capacity());
        assertEquals(54, BackpackTier.IRON.capacity());
        assertEquals(72, BackpackTier.GOLD.capacity());
        assertEquals(90, BackpackTier.DIAMOND.capacity());
        assertEquals(108, BackpackTier.NETHERITE.capacity());
    }

    @Test
    void rowsAreNineWide() {
        assertEquals(4, BackpackTier.LEATHER.rows());
        assertEquals(6, BackpackTier.IRON.rows());
        assertEquals(8, BackpackTier.GOLD.rows());
        assertEquals(10, BackpackTier.DIAMOND.rows());
        assertEquals(12, BackpackTier.NETHERITE.rows());
    }

    @Test
    void upgradeChainStopsAtNetherite() {
        assertEquals(BackpackTier.IRON, BackpackTier.LEATHER.next());
        assertEquals(BackpackTier.GOLD, BackpackTier.IRON.next());
        assertEquals(BackpackTier.DIAMOND, BackpackTier.GOLD.next());
        assertEquals(BackpackTier.NETHERITE, BackpackTier.DIAMOND.next());
        assertNull(BackpackTier.NETHERITE.next());
    }

    @Test
    void byCapacityFindsTheMatchingTier() {
        assertEquals(BackpackTier.LEATHER, BackpackTier.byCapacity(36));
        assertEquals(BackpackTier.IRON, BackpackTier.byCapacity(54));
        assertEquals(BackpackTier.GOLD, BackpackTier.byCapacity(72));
        assertEquals(BackpackTier.DIAMOND, BackpackTier.byCapacity(90));
        assertEquals(BackpackTier.NETHERITE, BackpackTier.byCapacity(108));
    }

    @Test
    void byCapacityReturnsNullForAnUnknownCapacity() {
        assertNull(BackpackTier.byCapacity(0));
        assertNull(BackpackTier.byCapacity(37));
        assertNull(BackpackTier.byCapacity(-1));
    }
}
