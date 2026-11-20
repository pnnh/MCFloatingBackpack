package pnnh.mcfloatingbackpack.compat;

import java.util.ArrayList;
import java.util.List;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 059: the levels the server goes by. The server config's {@code [compat].modLevels} is parsed the way the client's is ({@link
 * CompatPolicy#parse}); it is read every time a decision is made, so a changed file counts at once, and parsed again (with its mistakes
 * written to the log, once) only when the config hands over a different list. The level for a mod is the stricter of that and the client
 * config's ({@link EffectiveLevel}); the client config is the default (everything FULL) wherever it is not loaded, as on a dedicated
 * server. Before the config is loaded everything is FULL.
 */
public final class ServerCompatLevels {
    private record Snapshot(List<? extends String> raw, CompatPolicy policy) {
    }

    private static volatile Snapshot snapshot;

    private ServerCompatLevels() {
    }

    /** The server config's own levels. */
    public static CompatPolicy serverPolicy() {
        List<? extends String> raw = ThirdPartyConfig.rawModLevels();
        if (raw == null) {
            return CompatPolicy.DEFAULT;
        }
        Snapshot current = snapshot;
        if (current != null && current.raw() == raw) {
            return current.policy();
        }
        CompatPolicy.Parsed parsed = CompatPolicy.parse(new ArrayList<>(raw));
        for (CompatPolicy.Warning warning : parsed.warnings()) {
            MCFloatingBackpack.LOGGER.warn("Server config compat.modLevels: {}", warning.describe());
        }
        snapshot = new Snapshot(raw, parsed.policy());
        return parsed.policy();
    }

    /** The level the server goes by for the mod (vanilla and an unlisted mod are FULL). */
    public static CompatLevel effective(String modId) {
        return EffectiveLevel.effective(serverPolicy(), CompatConfig.policy(), modId);
    }

    /** Whether this mod's specific code may run on the server: only FULL says yes. */
    public static boolean specificAdaptation(String modId) {
        return effective(modId) == CompatLevel.FULL;
    }
}
