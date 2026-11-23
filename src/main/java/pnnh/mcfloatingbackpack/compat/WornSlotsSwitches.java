package pnnh.mcfloatingbackpack.compat;

import java.util.function.BooleanSupplier;

/**
 * 061: which start-up switch answers for which mod, and what a switch that cannot be read means (on). Pure logic, so it is tested without
 * the config ({@link WornSlotsConfig} hands it the real values).
 */
final class WornSlotsSwitches {
    private WornSlotsSwitches() {
    }

    static boolean enabled(String modId, BooleanSupplier curios, BooleanSupplier accessories) {
        try {
            return switch (modId == null ? "" : modId) {
                case "curios" -> curios.getAsBoolean();
                case "accessories" -> accessories.getAsBoolean();
                default -> true;
            };
        } catch (IllegalStateException notLoaded) {
            // a config value read before the config is loaded: the default, on
            return true;
        }
    }
}
