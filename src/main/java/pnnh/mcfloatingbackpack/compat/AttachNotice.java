package pnnh.mcfloatingbackpack.compat;

import java.util.HashSet;
import java.util.Set;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 020: the first time the panel is shown on another mod's container screen, the log says which mod id owns it and how
 * to turn the panel off there, so a player who hits a conflict does not have to guess the id. Once per mod and game
 * launch, never every frame.
 */
@OnlyIn(Dist.CLIENT)
public final class AttachNotice {
    private static final Set<String> NOTED = new HashSet<>();

    private AttachNotice() {
    }

    public static void onAttach(String ownerModId) {
        if (ownerModId == null || ownerModId.isEmpty() || CompatPolicy.VANILLA.equals(ownerModId)) {
            return;
        }
        if (NOTED.add(ownerModId)) {
            MCFloatingBackpack.LOGGER.info(
                    "The floating panel is shown on a screen of mod '{}'. If it conflicts with that mod, add \"{}=OFF\""
                            + " to compat.modLevels in config/mcfloatingbackpack-client.toml.",
                    ownerModId,
                    ownerModId);
        }
    }
}
