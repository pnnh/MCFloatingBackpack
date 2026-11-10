package pnnh.mcfloatingbackpack.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DiagLogicTest {
    @Test
    void eachLevelIncludesTheOnesBefore() {
        assertFalse(LogLevel.OFF.allows(LogLevel.ERROR));
        assertTrue(LogLevel.ERROR.allows(LogLevel.ERROR));
        assertFalse(LogLevel.ERROR.allows(LogLevel.WARN));
        assertTrue(LogLevel.WARN.allows(LogLevel.ERROR));
        assertTrue(LogLevel.WARN.allows(LogLevel.WARN));
        assertFalse(LogLevel.WARN.allows(LogLevel.INFO));
        assertTrue(LogLevel.DEBUG.allows(LogLevel.INFO));
        assertTrue(LogLevel.DEBUG.allows(LogLevel.DEBUG));
        assertFalse(LogLevel.DEBUG.allows(LogLevel.OFF));
    }

    @Test
    void ringKeepsTheLastEventsOldestFirst() {
        EventRing ring = new EventRing(3);
        for (int i = 1; i <= 5; i++) {
            ring.add("e" + i);
        }
        assertEquals(List.of("e3", "e4", "e5"), ring.snapshot());
        assertEquals(3, ring.size());
    }

    @Test
    void watchReportsOnlyWhatWasThereBeforeAndIsGoneNow() {
        BackpackWatch<String> watch = new BackpackWatch<>();
        assertTrue(watch.update(Map.of("a", "hotbar 0", "b", "slot 12")).isEmpty());
        assertTrue(watch.update(Map.of("a", "hotbar 0", "b", "slot 13", "c", "slot 3")).isEmpty());
        List<BackpackWatch.Gone<String>> gone = watch.update(Map.of("a", "hotbar 0"));
        assertEquals(2, gone.size());
        assertTrue(gone.contains(new BackpackWatch.Gone<>("b", "slot 13")));
        assertTrue(gone.contains(new BackpackWatch.Gone<>("c", "slot 3")));
        // reported once only
        assertTrue(watch.update(Map.of("a", "hotbar 0")).isEmpty());
    }

    @Test
    void aBackpackThatComesBackIsNotReportedAgain() {
        BackpackWatch<String> watch = new BackpackWatch<>();
        watch.update(Map.of("a", "hotbar 0"));
        assertEquals(1, watch.update(Map.of()).size());
        assertTrue(watch.update(Map.of("a", "hotbar 1")).isEmpty());
        assertTrue(watch.update(Map.of("a", "hotbar 1")).isEmpty());
    }

    @Test
    void resetForgetsEverything() {
        BackpackWatch<String> watch = new BackpackWatch<>();
        watch.update(Map.of("a", "hotbar 0"));
        watch.reset();
        assertTrue(watch.update(Map.of()).isEmpty());
    }
}
