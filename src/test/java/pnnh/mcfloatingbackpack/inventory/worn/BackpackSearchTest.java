package pnnh.mcfloatingbackpack.inventory.worn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.worn.BackpackSearch.Hit;
import pnnh.mcfloatingbackpack.inventory.worn.BackpackSearch.Kind;

/** 032: the one order in which a player's backpack is looked for, tested with fake players and fake sources. */
class BackpackSearchTest {
    private static final int MAIN = 0;
    private static final int OFF = 1;
    private static final int INV = 3;
    private static final String BACKPACK = "pack";

    /** A fake player: what is in each hand, in the inventory, and in each worn source (by source id). */
    private static final class Fake {
        String main = "";
        String off = "";
        final String[] inventory = new String[36];
        final Map<Integer, String[]> worn = new HashMap<>();
        final Map<Integer, boolean[]> hidden = new HashMap<>();

        Fake() {
            java.util.Arrays.fill(inventory, "");
        }

        Fake wearing(int sourceId, String... slots) {
            worn.put(sourceId, slots);
            return this;
        }

        Fake hiding(int sourceId, boolean... flags) {
            hidden.put(sourceId, flags);
            return this;
        }
    }

    private static final BackpackSearch.Probe<Fake, String> PROBE = new BackpackSearch.Probe<>() {
        @Override
        public String mainHand(Fake p) {
            return p.main;
        }

        @Override
        public String offHand(Fake p) {
            return p.off;
        }

        @Override
        public int inventorySlots(Fake p) {
            return p.inventory.length;
        }

        @Override
        public String inventory(Fake p, int index) {
            return p.inventory[index];
        }

        @Override
        public boolean isBackpack(String stack) {
            return BACKPACK.equals(stack);
        }
    };

    private static final class FakeSource implements WornSource<Fake, String> {
        final int id;
        final int order;
        boolean active = true;

        FakeSource(int id, int order) {
            this.id = id;
            this.order = order;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public int slotCount(Fake p) {
            return p.worn.getOrDefault(id, new String[0]).length;
        }

        @Override
        public String get(Fake p, int slot) {
            String[] slots = p.worn.getOrDefault(id, new String[0]);
            return slot < 0 || slot >= slots.length ? "" : slots[slot];
        }

        @Override
        public void set(Fake p, int slot, String stack) {
            p.worn.get(id)[slot] = stack;
        }

        @Override
        public boolean hiddenRender(Fake p, int slot) {
            boolean[] flags = p.hidden.get(id);
            return flags != null && slot < flags.length && flags[slot];
        }
    }

    private static BackpackSearch<Fake, String> search(WornSource<Fake, String>... sources) {
        BackpackSearch<Fake, String> search = new BackpackSearch<>(PROBE, MAIN, OFF, INV);
        for (WornSource<Fake, String> source : sources) {
            search.register(source);
        }
        return search;
    }

    // chest = 2 (order 0), curios = 4 (order 10), accessories = 5 (order 20): as 033 and 034 will have them
    private static final int CHEST = 2;
    private static final int CURIOS = 4;
    private static final int ACCESSORIES = 5;

    private static BackpackSearch<Fake, String> allThree() {
        return search(new FakeSource(CHEST, 0), new FakeSource(CURIOS, 10), new FakeSource(ACCESSORIES, 20));
    }

    @Test
    void theChestComesFirstThenTheFirstSourceAfterItThenTheNext() {
        BackpackSearch<Fake, String> search = allThree();
        Fake all = new Fake().wearing(CHEST, BACKPACK).wearing(CURIOS, BACKPACK).wearing(ACCESSORIES, BACKPACK);
        assertEquals(CHEST, search.firstWorn(all).sourceId());
        Fake noChest = new Fake().wearing(CHEST, "").wearing(CURIOS, BACKPACK).wearing(ACCESSORIES, BACKPACK);
        assertEquals(CURIOS, search.firstWorn(noChest).sourceId());
        Fake last = new Fake().wearing(ACCESSORIES, BACKPACK);
        assertEquals(ACCESSORIES, search.firstWorn(last).sourceId());
    }

    @Test
    void nothingWornGivesNull() {
        assertNull(allThree().firstWorn(new Fake()));
        assertNull(allThree().firstWorn(new Fake().wearing(CHEST, "x")));
    }

    @Test
    void theOrderIsByOrderNotByIdOrRegistrationTime() {
        // registered in the "wrong" order and with ids that do not follow the order
        BackpackSearch<Fake, String> search = search(new FakeSource(ACCESSORIES, 20), new FakeSource(CHEST, 0), new FakeSource(CURIOS, 10));
        Fake both = new Fake().wearing(CURIOS, BACKPACK).wearing(ACCESSORIES, BACKPACK);
        assertEquals(CURIOS, search.firstWorn(both).sourceId());
        BackpackSearch<Fake, String> reversedIds = search(new FakeSource(9, 1), new FakeSource(2, 2));
        Fake f = new Fake().wearing(9, BACKPACK).wearing(2, BACKPACK);
        assertEquals(9, reversedIds.firstWorn(f).sourceId(), "id 9 has the smaller order");
    }

    @Test
    void aSourceInTheMiddleCanBeAddedWithoutChangingTheOthers() {
        BackpackSearch<Fake, String> search = allThree();
        search.register(new FakeSource(6, 15));
        Fake f = new Fake().wearing(6, BACKPACK).wearing(ACCESSORIES, BACKPACK);
        assertEquals(6, search.firstWorn(f).sourceId());
        Fake g = new Fake().wearing(CURIOS, BACKPACK).wearing(6, BACKPACK);
        assertEquals(CURIOS, search.firstWorn(g).sourceId());
    }

    @Test
    void aSourceThatIsNotActiveIsSkipped() {
        FakeSource curios = new FakeSource(CURIOS, 10);
        BackpackSearch<Fake, String> search = search(new FakeSource(CHEST, 0), curios, new FakeSource(ACCESSORIES, 20));
        Fake f = new Fake().wearing(CURIOS, BACKPACK).wearing(ACCESSORIES, BACKPACK);
        assertEquals(CURIOS, search.firstWorn(f).sourceId());
        curios.active = false;
        assertEquals(ACCESSORIES, search.firstWorn(f).sourceId(), "asked at every search, so it takes effect at once");
        curios.active = true;
        assertEquals(CURIOS, search.firstWorn(f).sourceId());
    }

    @Test
    void theSlotWithinTheSourceIsReportedAndTheLowestSlotWins() {
        BackpackSearch<Fake, String> search = allThree();
        Hit hit = search.firstWorn(new Fake().wearing(CURIOS, "", BACKPACK, BACKPACK));
        assertEquals(Kind.WORN, hit.kind());
        assertEquals(2 - 1, hit.slot());
    }

    @Test
    void aHiddenSlotStillCountsAsWornButIsMarkedHiddenAndNoLaterBackpackTakesItsPlace() {
        BackpackSearch<Fake, String> search = allThree();
        Fake f = new Fake().wearing(CURIOS, BACKPACK).hiding(CURIOS, true).wearing(ACCESSORIES, BACKPACK);
        Hit hit = search.firstWorn(f);
        assertEquals(CURIOS, hit.sourceId());
        assertTrue(hit.hidden());
        assertEquals(CURIOS, search.resolve(f).sourceId(), "the panel key still opens it");
        assertFalse(search.firstWorn(new Fake().wearing(CURIOS, BACKPACK)).hidden());
    }

    @Test
    void aHandComesBeforeAnythingWorn() {
        BackpackSearch<Fake, String> search = allThree();
        Fake f = new Fake().wearing(CHEST, BACKPACK);
        f.main = BACKPACK;
        Hit hit = search.resolve(f);
        assertEquals(Kind.MAIN_HAND, hit.kind());
        assertEquals(MAIN, hit.sourceId());
        assertEquals(-1, hit.slot());
        f.main = "";
        f.off = BACKPACK;
        assertEquals(Kind.OFF_HAND, search.resolve(f).kind());
        f.main = BACKPACK;
        assertEquals(Kind.MAIN_HAND, search.resolve(f).kind(), "the main hand before the off hand");
    }

    @Test
    void wornComesBeforeTheInventoryAndTheInventoryTakesTheLowestIndex() {
        BackpackSearch<Fake, String> search = allThree();
        Fake f = new Fake().wearing(ACCESSORIES, BACKPACK);
        f.inventory[5] = BACKPACK;
        assertEquals(Kind.WORN, search.resolve(f).kind());
        Fake g = new Fake();
        g.inventory[7] = BACKPACK;
        g.inventory[3] = BACKPACK;
        Hit hit = search.resolve(g);
        assertEquals(Kind.INVENTORY, hit.kind());
        assertEquals(INV, hit.sourceId());
        assertEquals(3, hit.slot());
    }

    @Test
    void noBackpackAnywhereGivesNull() {
        assertNull(allThree().resolve(new Fake()));
    }

    @Test
    void theSameListGivesEachPlayerTheirOwnAnswer() {
        BackpackSearch<Fake, String> search = allThree();
        Fake a = new Fake().wearing(CURIOS, BACKPACK);
        Fake b = new Fake().wearing(ACCESSORIES, BACKPACK);
        Fake c = new Fake();
        assertEquals(CURIOS, search.resolve(a).sourceId());
        assertEquals(ACCESSORIES, search.resolve(b).sourceId());
        assertNull(search.resolve(c));
        assertEquals(CURIOS, search.resolve(a).sourceId());
    }

    @Test
    void anActiveSourceIsFoundByIdAndAnInactiveOrUnknownOneIsNot() {
        FakeSource curios = new FakeSource(CURIOS, 10);
        BackpackSearch<Fake, String> search = search(new FakeSource(CHEST, 0), curios);
        assertNotNull(search.activeSource(CHEST));
        assertNotNull(search.activeSource(CURIOS));
        assertNull(search.activeSource(ACCESSORIES));
        curios.active = false;
        assertNull(search.activeSource(CURIOS));
    }

    @Test
    void aSlotOutOfRangeReadsAsEmptyAndIsNeverAHit() {
        FakeSource source = new FakeSource(CHEST, 0);
        Fake f = new Fake().wearing(CHEST, BACKPACK);
        assertEquals("", source.get(f, -1));
        assertEquals("", source.get(f, 5));
        assertEquals(BACKPACK, source.get(f, 0));
    }

    @Test
    void theSameIdCannotBeRegisteredTwice() {
        BackpackSearch<Fake, String> search = search(new FakeSource(CHEST, 0));
        assertThrows(IllegalArgumentException.class, () -> search.register(new FakeSource(CHEST, 5)));
    }

    @Test
    void withOnlyTheChestItBehavesAsBefore() {
        // the order the game had before 032: main hand, off hand, chest, inventory 0-35
        BackpackSearch<Fake, String> search = search(new FakeSource(CHEST, 0));
        Fake f = new Fake().wearing(CHEST, BACKPACK);
        f.inventory[0] = BACKPACK;
        assertEquals(Kind.WORN, search.resolve(f).kind());
        assertEquals(0, search.resolve(f).slot());
        f.worn.put(CHEST, new String[] {""});
        assertEquals(Kind.INVENTORY, search.resolve(f).kind());
    }
}
