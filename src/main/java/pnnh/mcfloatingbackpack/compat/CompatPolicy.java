package pnnh.mcfloatingbackpack.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 020: the "mod id -> level" setting, parsed from the config's {@code modid=LEVEL} entries. A mod that is not listed is
 * at {@link CompatLevel#FULL}, and vanilla ({@code minecraft}) is always at FULL and cannot be configured. A bad entry
 * only costs itself (it is ignored, with a warning); it never spoils the other entries. Pure logic with no Minecraft
 * types, so it can be unit-tested.
 */
public final class CompatPolicy {
    public static final String VANILLA = "minecraft";
    public static final CompatPolicy DEFAULT = new CompatPolicy(Map.of());

    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_]+");

    /** What is wrong with one config entry. */
    public enum Problem {
        /** Not of the form modid=LEVEL, or the mod id is not a-z, 0-9, _. */
        MALFORMED,
        /** Vanilla cannot be configured. */
        VANILLA,
        /** The level is not FULL, GENERIC or OFF (treated as FULL). */
        UNKNOWN_LEVEL,
        /** The mod is listed more than once (the last entry wins). */
        DUPLICATE
    }

    /** One problem with one entry, for the log (English, {@link #describe()}) and for the chat (by {@link #problem()}). */
    public record Warning(Problem problem, String entry) {
        public String describe() {
            return switch (problem) {
                case MALFORMED -> "ignored '" + entry + "': expected 'modid=LEVEL' with a mod id of a-z, 0-9 and _";
                case VANILLA -> "ignored '" + entry + "': vanilla cannot be configured";
                case UNKNOWN_LEVEL -> "'" + entry + "': unknown level, treated as FULL (use FULL, GENERIC or OFF)";
                case DUPLICATE -> "'" + entry + "' is listed more than once, the last entry wins";
            };
        }
    }

    /** A parsed setting and what was wrong with the entries (one warning per ignored or corrected entry). */
    public record Parsed(CompatPolicy policy, List<Warning> warnings) {
    }

    private final Map<String, CompatLevel> levels;

    private CompatPolicy(Map<String, CompatLevel> levels) {
        this.levels = levels;
    }

    public static Parsed parse(List<String> entries) {
        Map<String, CompatLevel> levels = new HashMap<>();
        List<Warning> warnings = new ArrayList<>();
        if (entries != null) {
            for (String raw : entries) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                int eq = raw.indexOf('=');
                String id = eq < 0 ? raw.trim() : raw.substring(0, eq).trim();
                if (eq < 0 || !MOD_ID.matcher(id).matches()) {
                    warnings.add(new Warning(Problem.MALFORMED, raw.trim()));
                    continue;
                }
                if (id.equals(VANILLA)) {
                    warnings.add(new Warning(Problem.VANILLA, raw.trim()));
                    continue;
                }
                CompatLevel level = CompatLevel.parse(raw.substring(eq + 1));
                if (level == null) {
                    warnings.add(new Warning(Problem.UNKNOWN_LEVEL, raw.trim()));
                    level = CompatLevel.FULL;
                }
                if (levels.containsKey(id)) {
                    warnings.add(new Warning(Problem.DUPLICATE, id));
                }
                levels.put(id, level);
            }
        }
        return new Parsed(new CompatPolicy(Map.copyOf(levels)), List.copyOf(warnings));
    }

    /** The level for a mod: vanilla is always FULL, an unlisted mod is FULL. */
    public CompatLevel levelOf(String modId) {
        if (modId == null || modId.isEmpty() || modId.equals(VANILLA)) {
            return CompatLevel.FULL;
        }
        return levels.getOrDefault(modId, CompatLevel.FULL);
    }

    /** Whether the panel may be shown on the container screens that mod owns. Only OFF says no. */
    public boolean allowsInteraction(String ownerModId) {
        return levelOf(ownerModId) != CompatLevel.OFF;
    }

    /** Whether the mod-specific adaptation for that mod runs. Only FULL says yes. */
    public boolean specificAdaptation(String modId) {
        return levelOf(modId) == CompatLevel.FULL;
    }
}
