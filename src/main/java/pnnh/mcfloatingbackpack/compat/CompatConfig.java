package pnnh.mcfloatingbackpack.compat;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.CompatPolicy.Parsed;

/**
 * 020: the client config (config/mcfloatingbackpack-client.toml): one list, {@code modLevels}, of {@code modid=LEVEL}
 * entries. Its element validator accepts any string on purpose: {@code ModConfigSpec} throws a whole list back to
 * its default if one element fails validation, which would let a typo wipe every setting. {@link CompatPolicy} reads
 * the entries one by one instead.
 */
public final class CompatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_LEVELS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("compat");
        MOD_LEVELS = BUILDER
                .comment(
                        "How far the floating panel goes in working with another mod. One entry per mod: \"modid=LEVEL\".",
                        "A mod that is not listed is at FULL. Vanilla (minecraft) cannot be configured.",
                        "  FULL    - the mod-specific adaptations: code written for that mod, i.e. active compatibility and",
                        "            enhancements (today only for 'jei': it makes JEI keep clear of the panel). Default.",
                        "  GENERIC - none of that mod-specific code runs. For a mod there is no code for, GENERIC is the same as",
                        "            FULL. It does not decide whether the panel shows on the mod's screens or exchanges items",
                        "            (Ctrl+click) with its menus: that depends on how the mod implements its containers, and a mod",
                        "            that behaves like vanilla containers may work fine.",
                        "  OFF     - the panel is not shown on that mod's container screens: the panel and that UI are either/or.",
                        "            For a mod that only decorates other screens (jei) OFF is the same as GENERIC.",
                        "The first time the panel is shown on a mod's screen the log says which mod id to use.",
                        "Mistakes in this list are reported in the chat.",
                        "Example: modLevels = [\"sophisticatedbackpacks=OFF\", \"jei=GENERIC\"]")
                .translation("config.mcfloatingbackpack.modLevels")
                .defineListAllowEmpty("modLevels", List.of(), () -> "modid=FULL", element -> element instanceof String);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /** The raw list the policy below was parsed from, and the policy. Replaced as a pair. */
    private record Snapshot(List<? extends String> raw, CompatPolicy policy) {
    }

    private static volatile Snapshot snapshot;

    private CompatConfig() {
    }

    /**
     * The current policy. {@code get()} returns the same list object until the config is reloaded, so a changed
     * reference means a changed config: the entries are parsed again (and their problems logged) only then. Before the
     * config has loaded everything is FULL.
     */
    public static CompatPolicy policy() {
        List<? extends String> raw;
        try {
            raw = MOD_LEVELS.get();
        } catch (IllegalStateException notLoadedYet) {
            return CompatPolicy.DEFAULT;
        }
        Snapshot current = snapshot;
        if (current != null && current.raw() == raw) {
            return current.policy();
        }
        Parsed parsed = CompatPolicy.parse(new ArrayList<>(raw));
        for (CompatPolicy.Warning warning : parsed.warnings()) {
            MCFloatingBackpack.LOGGER.warn("Config compat.modLevels: {}", warning.describe());
            ConfigNotices.warning(warning);
        }
        snapshot = new Snapshot(raw, parsed.policy());
        return parsed.policy();
    }
}
