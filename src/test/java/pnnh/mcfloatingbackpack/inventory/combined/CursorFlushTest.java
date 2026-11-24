package pnnh.mcfloatingbackpack.inventory.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

class CursorFlushTest {
    /** A "stack" here is just a count; a place takes up to its room and returns what is left. */
    private static UnaryOperator<Integer> room(int[] room) {
        return count -> {
            int taken = Math.min(count, room[0]);
            room[0] -= taken;
            return count - taken;
        };
    }

    private static CursorFlush.Result<Integer> flush(int held, int[]... places) {
        int[] inventory = places[places.length - 1];
        List<UnaryOperator<Integer>> members = new java.util.ArrayList<>();
        for (int i = 0; i < places.length - 1; i++) {
            members.add(room(places[i]));
        }
        return CursorFlush.flush(held, members, room(inventory), c -> c <= 0, c -> c);
    }

    @Test
    void membersFirstInOrder() {
        CursorFlush.Result<Integer> r = flush(40, new int[] {25}, new int[] {100}, new int[] {100});
        assertEquals(List.of(25, 15), r.placedInMembers());
        assertEquals(0, r.placedInInventory());
        assertEquals(0, r.dropped());
    }

    @Test
    void thenTheInventoryThenTheGround() {
        CursorFlush.Result<Integer> r = flush(64, new int[] {10}, new int[] {0}, new int[] {20});
        assertEquals(List.of(10, 0), r.placedInMembers());
        assertEquals(20, r.placedInInventory());
        assertEquals(34, r.dropped());
    }

    @Test
    void nothingIsEverLost() {
        for (int held = 0; held <= 64; held += 7) {
            CursorFlush.Result<Integer> r = flush(held, new int[] {13}, new int[] {5}, new int[] {9});
            int sum = r.placedInMembers().stream().mapToInt(Integer::intValue).sum() + r.placedInInventory() + r.dropped();
            assertEquals(held, sum, "held " + held);
        }
    }

    @Test
    void anEmptyCursorTouchesNothing() {
        CursorFlush.Result<Integer> r = flush(0, new int[] {13}, new int[] {9});
        assertEquals(List.of(0), r.placedInMembers());
        assertEquals(0, r.placedInInventory());
    }

    @Test
    void noMembersGoesStraightToTheInventory() {
        CursorFlush.Result<Integer> r = CursorFlush.flush(
                10, List.<UnaryOperator<Integer>>of(), room(new int[] {4}), c -> c <= 0, c -> c);
        assertEquals(4, r.placedInInventory());
        assertEquals(6, r.dropped());
    }
}
