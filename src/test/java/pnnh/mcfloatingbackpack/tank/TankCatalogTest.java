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

/** 048: the 19 tanks, their capacities and which backpack takes which tier. */
class TankCatalogTest {
    @Test
    void thereAreNineteenTanksInTierThenLiquidOrder() {
        List<Tank> all = TankCatalog.all();
        assertEquals(19, all.size());
        assertEquals(new Tank(FluidKind.WATER, BackpackTier.LEATHER), all.get(0));
        assertEquals(new Tank(FluidKind.HONEY, BackpackTier.LEATHER), all.get(3));
        assertEquals(new Tank(FluidKind.WATER, BackpackTier.IRON), all.get(4));
        assertEquals(new Tank(FluidKind.HONEY, BackpackTier.NETHERITE), all.get(18));
        assertEquals(19, new HashSet<>(all).size());
    }

    @Test
    void leatherHasNoLavaTankAndNothingElseIsMissing() {
        assertFalse(TankCatalog.exists(FluidKind.LAVA, BackpackTier.LEATHER));
        int existing = 0;
        for (BackpackTier tier : TankCatalog.tiers()) {
            for (FluidKind kind : FluidKind.values()) {
                if (TankCatalog.exists(kind, tier)) {
                    existing++;
                }
            }
        }
        assertEquals(19, existing);
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
        assertEquals("netherite_honey_tank", TankCatalog.itemId(FluidKind.HONEY, BackpackTier.NETHERITE));
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
