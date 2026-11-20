package pnnh.mcfloatingbackpack.compat;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 058, 059: the {@code [compat]} section of the server config ({@code serverconfig/mcfloatingbackpack-server.toml}): (059) the server's own
 * {@code modLevels}, the same {@code "modid=LEVEL"} list as the client config's, read every time the server decides ({@link
 * ServerCompatLevels}); and (058) whether this mod's active compatibility with a third-party mod stays on when the installed version is
 * not one it was verified with ({@link ThirdPartyTable}), read when the server starts. The client config has a {@code compat} section of its own (the per-mod levels); they are different files.
 */
public final class ThirdPartyConfig {
    private static ModConfigSpec.BooleanValue allowUntestedVersions;
    private static ModConfigSpec.ConfigValue<List<? extends String>> modLevels;

    private ThirdPartyConfig() {
    }

    /** Adds the section to the server config's builder (called once, from {@code DiagConfig}). */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("compat");
        modLevels = builder.comment(
                        "How far the floating panel goes in working with another mod ON THE SERVER. One entry per mod: \"modid=LEVEL\".",
                        "A mod that is not listed is at FULL. Vanilla (minecraft) cannot be configured.",
                        "  FULL    - the mod-specific adaptations run on the server. Default.",
                        "  GENERIC - they do not run (today: putting items into an Integrated Terminals terminal, and the Curios and",
                        "            Accessories back slots as places a backpack can be worn). The generic Ctrl+click exchange with the",
                        "            mod's screens stays.",
                        "  OFF     - the server also refuses every item exchange between the panel and that mod's screens (the player is",
                        "            told once for each screen they open), and the adaptations above are off as well.",
                        "On a dedicated server this is the only place. In a single player world (and for the host of a LAN world) the",
                        "client's own compat.modLevels is read too, and the stricter of the two counts.",
                        "Mistakes in this list are written to the server log; an entry with a mistake only loses itself.",
                        "Changes take effect without reopening the world.",
                        "Example: modLevels = [\"sophisticatedstorage=OFF\", \"integratedterminals=GENERIC\"]")
                .defineListAllowEmpty("modLevels", List.of(), () -> "modid=FULL", element -> element instanceof String);
        allowUntestedVersions = builder.comment(
                        "Whether this mod's active compatibility with another mod (for now: Integrated Terminals, putting items from the",
                        "floating panel into a terminal's network) stays on when the installed version of that mod is not one this mod was",
                        "verified with. Off (the default): outside the verified versions the compatibility is switched off, the panel still",
                        "works on that mod's screens in the generic way, and the server log and every operator (op) at login get a warning.",
                        "On: the compatibility is used anyway, and the warnings still appear.",
                        "WARNING: the other mod may have changed in a way this mod does not expect. The result can be an error in this",
                        "mod's code, and in the worst case (a NoSuchMethodError and the like are not ordinary exceptions) a crash of the server.",
                        "Make a backup of the world before you turn this on, and if anything goes wrong set it back to false first.",
                        "Read when the server starts; a change needs the world to be reopened.")
                .define("allowUntestedVersions", false);
        builder.pop();
    }

    /** What the config says; false (not allowed) if the config is not loaded (it always is, once the server starts). */
    public static boolean allowUntested() {
        try {
            return allowUntestedVersions != null && allowUntestedVersions.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** The raw {@code modLevels} list the config holds now (the same object until the config is reloaded), or null if it is not loaded. */
    public static List<? extends String> rawModLevels() {
        try {
            return modLevels == null ? null : modLevels.get();
        } catch (IllegalStateException notLoaded) {
            return null;
        }
    }
}
