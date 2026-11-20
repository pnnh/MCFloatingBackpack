package pnnh.mcfloatingbackpack.compat;

import java.util.List;

import net.minecraft.server.MinecraftServer;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 058: works out, when the server starts, which third-party compatibilities are on ({@link CompatRuntimeState}) and writes a WARN line for
 * every installed mod that is outside the versions this mod was verified with ({@link ThirdPartyTable}). The state lives until the server
 * stops; a mod that is not installed is not mentioned anywhere.
 */
public final class ThirdPartyCompatService {
    private static volatile CompatRuntimeState state;
    private static volatile MinecraftServer server;

    private ThirdPartyCompatService() {
    }

    /** The server is starting (the server config has been read). */
    public static void start(MinecraftServer started) {
        server = started;
        List<String> problems = ThirdPartyTable.validate(ThirdPartyTable.ALL);
        if (!problems.isEmpty()) {
            MCFloatingBackpack.LOGGER.error("The table of third-party mod versions is not sound, no third-party compatibility is used: {}", problems);
            state = CompatRuntimeState.empty();
            return;
        }
        CompatRuntimeState evaluated = CompatRuntimeState.evaluate(ThirdPartyTable.ALL, InstalledMods::versionOf, ThirdPartyConfig.allowUntested());
        state = evaluated;
        for (CompatFinding finding : evaluated.findingsOutOfRange()) {
            boolean forced = evaluated.status(finding.entry().modId()).orElse(CompatStatus.OUT_OF_RANGE) == CompatStatus.FORCED;
            MCFloatingBackpack.LOGGER.warn(CompatLogText.warning(finding, forced));
        }
    }

    /** The server is stopping. */
    public static void stop() {
        state = null;
        server = null;
    }

    /** The state of this run; null before the server has started (nothing is on then). */
    public static CompatRuntimeState state() {
        return state;
    }

    static MinecraftServer server() {
        return server;
    }
}
