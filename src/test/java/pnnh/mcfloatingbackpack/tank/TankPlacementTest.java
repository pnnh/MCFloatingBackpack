package pnnh.mcfloatingbackpack.tank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.tank.TankCatalog.Tank;
import pnnh.mcfloatingbackpack.tank.TankPlacement.Verdict;

/** 048: where a tank may go: tier rule in all tool areas, one per liquid and capacity in the bottom area, the main area free. */
class TankPlacementTest {
    private static final String E = "";

    /** A stack is a string: {@code "<tier>_<liquid>"} is that tank, anything else is not a tank. */
    private static Tank tankOf(String stack) {
        for (Tank tank : TankCatalog.all()) {
            if (stack.equals(TankCatalog.itemId(tank.kind(), tank.tier()).replace("_tank", ""))) {
                return tank;
            }
        }
        return null;
    }

    private static String name(FluidKind kind, BackpackTier tier) {
        return TankCatalog.itemId(kind, tier).replace("_tank", "");
    }

    private static Verdict check(PanelArea area, String incoming, int slot, BackpackTier backpack, int stored, String... slots) {
        return TankPlacement.check(area, incoming, slot, Arrays.asList(slots), TankPlacementTest::tankOf, backpack, stored);
    }

    @Test
    void everyTankIntoEveryBackpackFollowsTheTierTable() {
        int checked = 0;
        for (BackpackTier backpack : BackpackTier.values()) {
            for (Tank tank : TankCatalog.all()) {
                for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.BOTTOM}) {
                    Verdict verdict = check(area, name(tank.kind(), tank.tier()), 0, backpack, 0, E, E, E);
                    Verdict expected = tank.tier().ordinal() <= backpack.ordinal() ? Verdict.OK : Verdict.TIER_TOO_HIGH;
                    assertEquals(expected, verdict, backpack + " " + area + " " + tank);
                }
                checked++;
            }
        }
        assertEquals(5 * 19, checked);
    }

    @Test
    void theMainAreaTakesAnyTank() {
        assertEquals(Verdict.OK, check(PanelArea.MAIN, "netherite_water", 0, BackpackTier.LEATHER, 0, E, E));
    }

    @Test
    void aSecondTankOfTheSameLiquidIsRefusedWhateverItsTier() {
        assertEquals(Verdict.DUPLICATE_KIND, check(PanelArea.BOTTOM, "iron_water", 1, BackpackTier.NETHERITE, 0, "leather_water", E, E));
        assertEquals(Verdict.DUPLICATE_KIND, check(PanelArea.BOTTOM, "leather_water", 2, BackpackTier.NETHERITE, 0, "gold_water", "dirt", E));
    }

    @Test
    void replacingTheTankOfTheSameLiquidInItsOwnSlotIsFine() {
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "gold_water", 0, BackpackTier.NETHERITE, 0, "leather_water", E, E));
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "iron_water", 0, BackpackTier.NETHERITE, 0, "iron_water", E, E));
    }

    @Test
    void otherLiquidsDoNotGetInTheWay() {
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "iron_oil", 1, BackpackTier.NETHERITE, 0, "iron_water", E, E));
        List<String> placed = new ArrayList<>(List.of(E, E, E, E, E));
        FluidKind[] kinds = FluidKind.values();
        for (int i = 0; i < kinds.length; i++) {
            String stack = name(kinds[i], BackpackTier.IRON);
            assertEquals(Verdict.OK, TankPlacement.check(PanelArea.BOTTOM, stack, i, placed, TankPlacementTest::tankOf, BackpackTier.NETHERITE, 0));
            placed.set(i, stack);
        }
    }

    @Test
    void theLiquidAlreadyStoredMustFitTheTank() {
        assertEquals(Verdict.STORE_EXCEEDS_CAPACITY, check(PanelArea.BOTTOM, "leather_water", 0, BackpackTier.NETHERITE, 10_000, E, E));
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "iron_water", 0, BackpackTier.NETHERITE, 10_000, E, E));
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "leather_water", 0, BackpackTier.NETHERITE, 8_000, E, E));
        assertEquals(Verdict.STORE_EXCEEDS_CAPACITY, check(PanelArea.BOTTOM, "leather_water", 0, BackpackTier.NETHERITE, 8_001, E, E));
    }

    @Test
    void theSideAreasTakeTwoOfTheSameLiquidAndDoNotCheckCapacity() {
        assertEquals(Verdict.OK, check(PanelArea.LEFT, "iron_water", 1, BackpackTier.IRON, 999_999, "iron_water", E, E));
        assertEquals(Verdict.OK, check(PanelArea.RIGHT, "leather_water", 1, BackpackTier.IRON, 999_999, "leather_water", E, E));
        assertEquals(Verdict.TIER_TOO_HIGH, check(PanelArea.LEFT, "gold_water", 1, BackpackTier.IRON, 0, E, E));
    }

    @Test
    void notATankOrNoTierKnown() {
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "dirt", 0, BackpackTier.LEATHER, 0, "leather_water", E));
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, E, 0, BackpackTier.LEATHER, 0, E));
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "leather_water", 0, null, 0, E, E));
        assertEquals(Verdict.TIER_TOO_HIGH, check(PanelArea.BOTTOM, "iron_water", 0, null, 0, E, E));
    }

    @Test
    void anAreaThatAlreadyHasTwoOfTheLiquidTakesNoThirdButAllowsReplacingOne() {
        assertEquals(Verdict.DUPLICATE_KIND, check(PanelArea.BOTTOM, "iron_water", 2, BackpackTier.NETHERITE, 0, "iron_water", "gold_water", E));
        assertEquals(Verdict.OK, check(PanelArea.BOTTOM, "iron_water", 1, BackpackTier.NETHERITE, 0, "iron_water", "gold_water", E));
    }

    @Test
    void randomPlacementsKeepOneTankPerLiquidAndNeverExceedTheBackpackTier() {
        Random random = new Random(48);
        BackpackTier[] backpacks = BackpackTier.values();
        List<Tank> all = TankCatalog.all();
        for (int round = 0; round < 1000; round++) {
            BackpackTier backpack = backpacks[random.nextInt(backpacks.length)];
            int size = 3 + random.nextInt(9);
            List<String> slots = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                slots.add(E);
            }
            for (int step = 0; step < 40; step++) {
                Tank tank = all.get(random.nextInt(all.size()));
                String stack = name(tank.kind(), tank.tier());
                int slot = random.nextInt(size);
                int stored = random.nextInt(20) == 0 ? random.nextInt(300_000) : 0;
                Verdict verdict = TankPlacement.check(PanelArea.BOTTOM, stack, slot, slots, TankPlacementTest::tankOf, backpack, stored);
                if (verdict == Verdict.OK) {
                    assertTrue(TankCatalog.allowedIn(tank.tier(), backpack));
                    assertTrue(stored <= TankCatalog.capacityMb(tank.tier()));
                    slots.set(slot, stack);
                }
                if (random.nextInt(6) == 0) {
                    slots.set(random.nextInt(size), E);
                }
                for (FluidKind kind : FluidKind.values()) {
                    int count = 0;
                    for (String s : slots) {
                        Tank t = tankOf(s);
                        if (t != null && t.kind() == kind) {
                            count++;
                        }
                    }
                    assertTrue(count <= 1, "round " + round + ": " + slots);
                }
            }
        }
    }
}
