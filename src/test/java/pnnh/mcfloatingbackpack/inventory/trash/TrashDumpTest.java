package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 022: which main-area slots a dump takes. Stacks are plain strings; the four tests are given by the caller. */
class TrashDumpTest {
    private static final Set<String> TRASH_TAGGED = Set.of("cobble", "flesh", "pickaxe", "chest", "egg");
    private static final Set<String> PROTECTED = Set.of("chest", "egg");
    private static final Set<String> EXTRA_DATA = Set.of("enchanted_pickaxe");

    private static int[] select(String... stacks) {
        List<String> list = List.of(stacks);
        return TrashDump.select(
                list.size(),
                list::get,
                String::isEmpty,
                s -> TRASH_TAGGED.contains(s.replace("enchanted_", "")),
                PROTECTED::contains,
                s -> !EXTRA_DATA.contains(s));
    }

    @Test
    void takesTaggedUnprotectedPlainStacksInSlotOrder() {
        assertArrayEquals(new int[] {0, 2, 4}, select("cobble", "diamond", "flesh", "", "pickaxe"));
    }

    @Test
    void skipsEmptySlotsUntaggedProtectedAndStacksWithData() {
        assertArrayEquals(new int[] {0}, select("cobble", "", "diamond", "chest", "egg", "enchanted_pickaxe"));
    }

    @Test
    void returnsNothingWhenNothingQualifies() {
        assertArrayEquals(new int[0], select("diamond", "", "chest"));
        assertArrayEquals(new int[0], select());
    }

    @Test
    void neverReturnsAnIndexOutsideTheArea() {
        List<String> list = List.of("cobble", "cobble", "cobble");
        int[] picked = TrashDump.select(2, list::get, String::isEmpty, s -> true, s -> false, s -> true);
        assertArrayEquals(new int[] {0, 1}, picked);
    }
}
