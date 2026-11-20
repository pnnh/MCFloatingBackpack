package pnnh.mcfloatingbackpack.compat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 058: what checking one {@link ThirdPartyCompat} row found. {@code subject} is the mod that is out of range (the row's own mod, or one
 * of the mods it also needs); for a row in range it is the row's own mod. Pure logic.
 */
public record CompatFinding(
        ThirdPartyCompat entry,
        VersionVerdict verdict,
        String subjectModId,
        String subjectName,
        String installedVersion,
        List<VersionRange> ranges) {

    /**
     * @param installedVersion the installed version text of a mod, empty when it is not installed
     * @return empty when the row does not apply: its own mod is not installed, or one of the mods it needs is not (then its own mod could
     *     not be running either, so nothing is judged and nothing is said)
     */
    public static Optional<CompatFinding> check(ThirdPartyCompat entry, Function<String, Optional<String>> installedVersion) {
        Optional<String> own = installedVersion.apply(entry.modId());
        if (own.isEmpty()) {
            return Optional.empty();
        }
        for (Requirement required : entry.also()) {
            if (installedVersion.apply(required.modId()).isEmpty()) {
                return Optional.empty();
            }
        }
        VersionVerdict verdict = VersionChecker.check(entry.supported(), own.get());
        if (verdict.outOfRange()) {
            return Optional.of(new CompatFinding(entry, verdict, entry.modId(), entry.displayName(), own.get(), entry.supported()));
        }
        for (Requirement required : entry.also()) {
            String installed = installedVersion.apply(required.modId()).orElseThrow();
            VersionVerdict requiredVerdict = VersionChecker.check(required.supported(), installed);
            if (requiredVerdict.outOfRange()) {
                return Optional.of(new CompatFinding(
                        entry, requiredVerdict, required.modId(), required.displayName(), installed, required.supported()));
            }
        }
        return Optional.of(new CompatFinding(entry, VersionVerdict.IN_RANGE, entry.modId(), entry.displayName(), own.get(), entry.supported()));
    }
}
