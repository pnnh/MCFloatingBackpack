package pnnh.mcfloatingbackpack.compat;

import java.util.Map;
import java.util.Optional;

import net.neoforged.fml.ModList;

import pnnh.mcfloatingbackpack.version.VersionDebug;

/**
 * 058: the versions of the mods that are installed, for {@link CompatRuntimeState#evaluate}. The debug switch
 * {@code -Pmcfb.fakeModVersion=modid=X} (see {@link VersionDebug}) changes the version of a mod that is installed; it cannot make a mod
 * that is not installed appear (the compatibility code of such a mod could not even be loaded).
 */
public final class InstalledMods {
    private InstalledMods() {
    }

    /** The version text of the mod, empty when it is not installed. */
    public static Optional<String> versionOf(String modId) {
        Optional<String> real = ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getVersion().toString());
        if (real.isEmpty()) {
            return real;
        }
        Map<String, String> fake = VersionDebug.fakeModVersions();
        return fake.containsKey(modId) ? Optional.of(fake.get(modId)) : real;
    }
}
