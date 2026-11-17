package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 057: the count of stacks left as empty places, per thread. */
class LostCellsTest {
    @Test
    void takeGivesWhatWasAddedAndStartsAgain() {
        LostCells.reset();
        LostCells.add(2);
        LostCells.add(3);
        assertEquals(5, LostCells.take());
        assertEquals(0, LostCells.take());
    }

    @Test
    void resetThrowsTheCountAwayAndANegativeNumberAddsNothing() {
        LostCells.add(4);
        LostCells.reset();
        assertEquals(0, LostCells.take());
        LostCells.add(-7);
        assertEquals(0, LostCells.take());
    }

    @Test
    void anotherThreadCountsOnItsOwn() throws InterruptedException {
        LostCells.reset();
        LostCells.add(1);
        int[] other = new int[1];
        Thread thread = new Thread(() -> {
            LostCells.add(9);
            other[0] = LostCells.take();
        });
        thread.start();
        thread.join();
        assertEquals(9, other[0]);
        assertEquals(1, LostCells.take(), "this thread's count was not touched");
    }
}
