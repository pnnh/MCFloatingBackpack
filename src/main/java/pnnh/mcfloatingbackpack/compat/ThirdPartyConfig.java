package pnnh.mcfloatingbackpack.compat;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 058: the {@code [compat]} section of the server config ({@code serverconfig/mcfloatingbackpack-server.toml}): whether this mod's active
 * compatibility with a third-party mod stays on when the installed version is not one it was verified with ({@link ThirdPartyTable}). Read
 * when the server starts. The client config has a {@code compat} section of its own (the per-mod levels); they are different files.
 */
public final class ThirdPartyConfig {
    private static ModConfigSpec.BooleanValue allowUntestedVersions;

    private ThirdPartyConfig() {
    }

    /** Adds the section to the server config's builder (called once, from {@code DiagConfig}). */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("compat");
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
}
