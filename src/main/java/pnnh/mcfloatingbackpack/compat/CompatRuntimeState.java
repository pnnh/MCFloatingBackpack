package pnnh.mcfloatingbackpack.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 058: which of the third-party compatibilities are on in this run of the server. Worked out once when the server starts
 * ({@link #evaluate}); after that only {@link #fail} changes it, and only to FAILED. A mod that is not installed has no row here at all.
 * Safe for several threads. Pure logic.
 */
public final class CompatRuntimeState {
    private final Map<String, CompatFinding> findings;
    private final Map<String, CompatStatus> statuses = new ConcurrentHashMap<>();

    private CompatRuntimeState(Map<String, CompatFinding> findings, Map<String, CompatStatus> initial) {
        this.findings = findings;
        this.statuses.putAll(initial);
    }

    /** A state with nothing installed. */
    public static CompatRuntimeState empty() {
        return new CompatRuntimeState(Map.of(), Map.of());
    }

    /**
     * @param installedVersion the installed version text of a mod, empty when it is not installed
     * @param allowUntested the server config's {@code allowUntestedVersions}
     */
    public static CompatRuntimeState evaluate(
            List<ThirdPartyCompat> table, Function<String, Optional<String>> installedVersion, boolean allowUntested) {
        Map<String, CompatFinding> findings = new LinkedHashMap<>();
        Map<String, CompatStatus> statuses = new LinkedHashMap<>();
        for (ThirdPartyCompat entry : table) {
            Optional<CompatFinding> found = CompatFinding.check(entry, installedVersion);
            if (found.isEmpty()) {
                continue;
            }
            findings.put(entry.modId(), found.get());
            if (!found.get().verdict().outOfRange()) {
                statuses.put(entry.modId(), CompatStatus.ACTIVE);
            } else {
                statuses.put(entry.modId(), allowUntested ? CompatStatus.FORCED : CompatStatus.OUT_OF_RANGE);
            }
        }
        return new CompatRuntimeState(findings, statuses);
    }

    public Optional<CompatStatus> status(String modId) {
        return Optional.ofNullable(statuses.get(modId));
    }

    /** Whether the active compatibility for this mod may run now. */
    public boolean isActive(String modId) {
        CompatStatus status = statuses.get(modId);
        return status != null && status.isOn();
    }

    /**
     * Switches a compatibility that was on off for the rest of this run.
     *
     * @return true only the first time, so the failure is reported once
     */
    public boolean fail(String modId) {
        boolean[] first = {false};
        statuses.computeIfPresent(modId, (key, status) -> {
            if (status.isOn()) {
                first[0] = true;
                return CompatStatus.FAILED;
            }
            return status;
        });
        return first[0];
    }

    /** The rows that are outside the verified versions (off, or forced on), in table order; a row that failed is not among them. */
    public List<CompatFinding> findingsOutOfRange() {
        List<CompatFinding> out = new ArrayList<>();
        for (Map.Entry<String, CompatFinding> found : findings.entrySet()) {
            CompatStatus status = statuses.get(found.getKey());
            if (status == CompatStatus.OUT_OF_RANGE || status == CompatStatus.FORCED) {
                out.add(found.getValue());
            }
        }
        return out;
    }

    /** The row for a mod, if it is installed and was judged. */
    public Optional<ThirdPartyCompat> entry(String modId) {
        return Optional.ofNullable(findings.get(modId)).map(CompatFinding::entry);
    }
}
