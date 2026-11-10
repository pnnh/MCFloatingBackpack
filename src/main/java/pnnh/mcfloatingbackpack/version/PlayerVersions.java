package pnnh.mcfloatingbackpack.version;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 039: what the server knows about the version of this mod each connected player runs, in memory for as long as they are logged in:
 * "not reported yet" after the server asked, "no version check" for a client too old to have this feature, or the version the client
 * reported. It is only for the log and the diagnostics; nothing in the game depends on it.
 */
public final class PlayerVersions {
    public static final String NOT_REPORTED = "尚未报告 / not reported yet";
    public static final String NO_VERSION_CHECK = "无版本检测（版本过旧）/ no version check (too old)";
    public static final String UNKNOWN = "未知 / unknown";

    private static final Map<UUID, String> VERSIONS = new ConcurrentHashMap<>();

    private PlayerVersions() {
    }

    /** The server has sent its version to the player and waits for the answer. */
    public static void expectReport(UUID player) {
        VERSIONS.put(player, NOT_REPORTED);
    }

    public static void noVersionCheck(UUID player) {
        VERSIONS.put(player, NO_VERSION_CHECK);
    }

    /** Whether the server asked this player and has not had the answer yet. */
    public static boolean expectsReport(UUID player) {
        return NOT_REPORTED.equals(VERSIONS.get(player));
    }

    public static void report(UUID player, String version) {
        VERSIONS.put(player, version == null || version.isBlank() ? UNKNOWN : version);
    }

    public static void forget(UUID player) {
        VERSIONS.remove(player);
    }

    /** What to write in a log line for this player; "unknown" when nothing is recorded. */
    public static String describe(UUID player) {
        return VERSIONS.getOrDefault(player, UNKNOWN);
    }
}
