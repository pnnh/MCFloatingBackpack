package pnnh.mcfloatingbackpack.compat;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 060: the client's own answer to "are the versions of the mods installed here ones this mod's active compatibility was verified with?". The
 * client has none of the server's runtime state (only a server has it) and no word of the server's "allow untested versions" setting, so it goes by the
 * table alone: outside the verified versions it does not use the compatibility, and says nothing (the warnings are the server's). The answer
 * does not change during a run (installed versions do not), so it is kept.
 */
public final class ClientCompatVersions {
    private static final Map<String, Boolean> KNOWN = new ConcurrentHashMap<>();

    private ClientCompatVersions() {
    }

    /** Whether the row's mod, and the mods it needs, are installed and all inside their verified versions. */
    public static boolean inRange(ThirdPartyCompat row) {
        return KNOWN.computeIfAbsent(row.modId(), id -> inRange(row, InstalledMods::versionOf));
    }

    /** The same question for any lookup of installed versions (the unit tests use it). */
    static boolean inRange(ThirdPartyCompat row, Function<String, Optional<String>> installedVersion) {
        return CompatFinding.check(row, installedVersion).map(found -> found.verdict() == VersionVerdict.IN_RANGE).orElse(false);
    }
}
