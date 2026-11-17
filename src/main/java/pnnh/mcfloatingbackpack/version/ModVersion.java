package pnnh.mcfloatingbackpack.version;

import net.neoforged.fml.ModList;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/** 057: this mod's version as the mod loader has it (shared by the world marker and the snapshot folder's version check). */
public final class ModVersion {
    private ModVersion() {
    }

    /** This version's number, or null when it cannot be told. */
    public static String current() {
        return ModList.get().getModContainerById(MCFloatingBackpack.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }
}
