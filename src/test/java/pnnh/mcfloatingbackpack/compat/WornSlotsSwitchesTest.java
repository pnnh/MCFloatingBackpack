package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 061: the start-up switches of the dedicated slots. Which switch answers for which mod, and what an unreadable one means. */
class WornSlotsSwitchesTest {

    @Test
    void eachSwitchIsItsOwn() {
        assertFalse(WornSlotsSwitches.enabled("curios", () -> false, () -> true));
        assertTrue(WornSlotsSwitches.enabled("accessories", () -> false, () -> true));
        assertTrue(WornSlotsSwitches.enabled("curios", () -> true, () -> false));
        assertFalse(WornSlotsSwitches.enabled("accessories", () -> true, () -> false));
    }

    @Test
    void aModThatHasNoSwitchIsAlwaysOn() {
        assertTrue(WornSlotsSwitches.enabled("somemod", () -> false, () -> false));
        assertTrue(WornSlotsSwitches.enabled(null, () -> false, () -> false));
        assertTrue(WornSlotsSwitches.enabled("", () -> false, () -> false));
    }

    @Test
    void aReadThatFailsBecauseTheConfigIsNotLoadedMeansOn() {
        java.util.function.BooleanSupplier notLoaded = () -> {
            throw new IllegalStateException("not loaded");
        };
        assertTrue(WornSlotsSwitches.enabled("curios", notLoaded, notLoaded));
        assertTrue(WornSlotsSwitches.enabled("accessories", notLoaded, notLoaded));
    }
}
