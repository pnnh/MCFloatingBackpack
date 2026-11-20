package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CompatRuntimeStateTest {
    private static final List<ThirdPartyCompat> TABLE = List.of(
            new ThirdPartyCompat("a", "Mod A", List.of(VersionRange.of("1.0.0", "2.0.0")), List.of(), null),
            new ThirdPartyCompat("b", "Mod B", List.of(VersionRange.of("5.0.0", "6.0.0")), List.of(), null));

    private static CompatRuntimeState state(Map<String, String> installed, boolean allow) {
        return CompatRuntimeState.evaluate(TABLE, id -> Optional.ofNullable(installed.get(id)), allow);
    }

    @Test
    void inRangeIsActive() {
        CompatRuntimeState state = state(Map.of("a", "1.5.0"), false);
        assertEquals(Optional.of(CompatStatus.ACTIVE), state.status("a"));
        assertTrue(state.isActive("a"));
        assertEquals(List.of(), state.findingsOutOfRange());
    }

    @Test
    void outOfRangeIsOffWhenNotForced() {
        CompatRuntimeState state = state(Map.of("a", "3.0.0"), false);
        assertEquals(Optional.of(CompatStatus.OUT_OF_RANGE), state.status("a"));
        assertFalse(state.isActive("a"));
        assertEquals(1, state.findingsOutOfRange().size());
        assertEquals(VersionVerdict.ABOVE_ALL, state.findingsOutOfRange().get(0).verdict());
    }

    @Test
    void outOfRangeIsOnWhenTheConfigAllowsItAndStillReported() {
        CompatRuntimeState state = state(Map.of("a", "3.0.0"), true);
        assertEquals(Optional.of(CompatStatus.FORCED), state.status("a"));
        assertTrue(state.isActive("a"));
        assertEquals(1, state.findingsOutOfRange().size());
    }

    @Test
    void inRangeWithTheConfigOnIsPlainActiveAndNotReported() {
        CompatRuntimeState state = state(Map.of("a", "1.5.0"), true);
        assertEquals(Optional.of(CompatStatus.ACTIVE), state.status("a"));
        assertEquals(List.of(), state.findingsOutOfRange());
    }

    @Test
    void aModThatIsNotInstalledHasNoStatus() {
        CompatRuntimeState state = state(Map.of("a", "1.5.0"), false);
        assertEquals(Optional.empty(), state.status("b"));
        assertFalse(state.isActive("b"));
        assertFalse(state.isActive("unknown"));
    }

    @Test
    void failSwitchesOffOnceAndReportsOnlyTheFirstTime() {
        CompatRuntimeState state = state(Map.of("a", "1.5.0"), false);
        assertTrue(state.fail("a"));
        assertFalse(state.fail("a"));
        assertEquals(Optional.of(CompatStatus.FAILED), state.status("a"));
        assertFalse(state.isActive("a"));
    }

    @Test
    void aForcedCompatibilityCanFailToo() {
        CompatRuntimeState state = state(Map.of("a", "3.0.0"), true);
        assertTrue(state.fail("a"));
        assertEquals(Optional.of(CompatStatus.FAILED), state.status("a"));
        assertEquals(List.of(), state.findingsOutOfRange());
    }

    @Test
    void aCompatibilityThatIsOffCannotFail() {
        CompatRuntimeState state = state(Map.of("a", "3.0.0"), false);
        assertFalse(state.fail("a"));
        assertEquals(Optional.of(CompatStatus.OUT_OF_RANGE), state.status("a"));
        assertFalse(state.fail("not-there"));
    }

    @Test
    void outOfRangeRowsComeInTableOrder() {
        CompatRuntimeState state = state(Map.of("b", "9.0.0", "a", "0.1.0"), false);
        assertEquals(List.of("a", "b"), state.findingsOutOfRange().stream().map(f -> f.entry().modId()).toList());
    }

    @Test
    void emptyStateIsActiveForNothing() {
        assertFalse(CompatRuntimeState.empty().isActive("a"));
        assertEquals(List.of(), CompatRuntimeState.empty().findingsOutOfRange());
    }
}
