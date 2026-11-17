package pnnh.mcfloatingbackpack.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.tank.TankCatalog.Tank;

/** 048, 056: the 12 tanks, their capacities and which backpack takes which tier. */
class TankCatalogTest {
    @Test
    void thereAreTwelveTanksInTierThenLiquidOrder() {
        List<Tank> all = TankCatalog.all();
        assertEquals(List.of(
                new Tank(FluidKind.WATER, BackpackTier.LEATHER),
                new Tank(FluidKind.WATER, BackpackTier.IRON), new Tank(FluidKind.OIL, BackpackTier.IRON), new Tank(FluidKind.LAVA, BackpackTier.IRON),
                new Tank(FluidKind.WATER, BackpackTier.GOLD), new Tank(FluidKind.OIL, BackpackTier.GOLD), new Tank(FluidKind.LAVA, BackpackTier.GOLD),
                new Tank(FluidKind.MILK, BackpackTier.GOLD), new Tank(FluidKind.HONEY, BackpackTier.GOLD),
                new Tank(FluidKind.WATER, BackpackTier.NETHERITE), new Tank(FluidKind.OIL, BackpackTier.NETHERITE),
                new Tank(FluidKind.LAVA, BackpackTier.NETHERITE)), all);
        assertEquals(12, new HashSet<>(all).size());
    }

    @Test
    void waterHasFourTiersOilAndLavaStartAtIronAndMilkAndHoneyAreGoldOnly() {
        for (BackpackTier tier : TankCatalog.tiers()) {
            assertTrue(TankCatalog.exists(FluidKind.WATER, tier));
            assertEquals(tier != BackpackTier.LEATHER, TankCatalog.exists(FluidKind.OIL, tier), "oil " + tier);
            assertEquals(tier != BackpackTier.LEATHER, TankCatalog.exists(FluidKind.LAVA, tier), "lava " + tier);
            assertEquals(tier == BackpackTier.GOLD, TankCatalog.exists(FluidKind.MILK, tier), "milk " + tier);
            assertEquals(tier == BackpackTier.GOLD, TankCatalog.exists(FluidKind.HONEY, tier), "honey " + tier);
        }
        int existing = 0;
        for (BackpackTier tier : TankCatalog.tiers()) {
            for (FluidKind kind : FluidKind.values()) {
                if (TankCatalog.exists(kind, tier)) {
                    existing++;
                }
            }
        }
        assertEquals(12, existing);
        for (FluidKind kind : FluidKind.values()) {
            assertFalse(TankCatalog.exists(kind, BackpackTier.DIAMOND), "no diamond tanks");
        }
    }

    @Test
    void capacitiesAreEightSixteenSixtyFourAndTwoHundredFiftySixBuckets() {
        assertEquals(8_000, TankCatalog.capacityMb(BackpackTier.LEATHER));
        assertEquals(16_000, TankCatalog.capacityMb(BackpackTier.IRON));
        assertEquals(64_000, TankCatalog.capacityMb(BackpackTier.GOLD));
        assertEquals(256_000, TankCatalog.capacityMb(BackpackTier.NETHERITE));
        assertThrows(IllegalArgumentException.class, () -> TankCatalog.capacityMb(BackpackTier.DIAMOND));
    }

    @Test
    void itemIdsFollowTierThenLiquid() {
        assertEquals("leather_water_tank", TankCatalog.itemId(FluidKind.WATER, BackpackTier.LEATHER));
        assertEquals("iron_oil_tank", TankCatalog.itemId(FluidKind.OIL, BackpackTier.IRON));
        assertEquals("gold_honey_tank", TankCatalog.itemId(FluidKind.HONEY, BackpackTier.GOLD));
        Set<String> ids = new HashSet<>();
        for (Tank tank : TankCatalog.all()) {
            assertTrue(ids.add(TankCatalog.itemId(tank.kind(), tank.tier())));
        }
    }

    @Test
    void aTankGoesIntoABackpackOfItsTierOrHigher() {
        boolean[][] expected = {
            // tank:  leather iron   gold   netherite
            /* leather    */ {true, false, false, false},
            /* iron       */ {true, true, false, false},
            /* gold       */ {true, true, true, false},
            /* diamond    */ {true, true, true, false},
            /* netherite  */ {true, true, true, true},
        };
        BackpackTier[] backpacks = BackpackTier.values();
        for (int b = 0; b < backpacks.length; b++) {
            List<BackpackTier> tanks = TankCatalog.tiers();
            for (int t = 0; t < tanks.size(); t++) {
                assertEquals(expected[b][t], TankCatalog.allowedIn(tanks.get(t), backpacks[b]), backpacks[b] + " / " + tanks.get(t));
            }
        }
    }

    @Test
    void anUnknownBackpackTierTakesOnlyLeatherTanks() {
        assertTrue(TankCatalog.allowedIn(BackpackTier.LEATHER, null));
        assertFalse(TankCatalog.allowedIn(BackpackTier.IRON, null));
    }

    @Test
    void honeyMovesAQuarterBucketAndTheRestABucket() {
        assertEquals(250, FluidKind.HONEY.perUseMb());
        for (FluidKind kind : FluidKind.values()) {
            if (kind != FluidKind.HONEY) {
                assertEquals(1000, kind.perUseMb());
            }
        }
        assertTrue(FluidKind.OIL.locksFluid());
        assertFalse(FluidKind.WATER.locksFluid());
        assertEquals(FluidKind.MILK, FluidKind.byId("milk"));
        assertEquals(null, FluidKind.byId("nope"));
    }
}
