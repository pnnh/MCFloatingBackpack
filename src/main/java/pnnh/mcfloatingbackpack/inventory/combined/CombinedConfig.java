package pnnh.mcfloatingbackpack.inventory.combined;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 065: the {@code [combined]} section of the server config: whether the combined backpack does anything. Read every time it is asked, so a
 * change in the file takes effect at the next check (once a second for an open combined panel); the section is defined once, from
 * {@code DiagConfig}.
 */
public final class CombinedConfig {
    private static ModConfigSpec.BooleanValue enabled;

    private CombinedConfig() {
    }

    /** Adds the section to the server config's builder (called once, from {@code DiagConfig}). */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("combined");
        enabled = builder.comment(
                        "Whether the Combined Backpack works. On (the default): a worn Combined Backpack connects the floating backpacks",
                        "the player carries (of its tier or lower) and its key opens a panel with a tab for each of them.",
                        "Off: the item, its recipes and everything stored stay as they are, but the key does not open the panel and a panel",
                        "that is open is closed to the ordinary one-backpack panel at the next check (within a second).",
                        "Nothing is stored in the Combined Backpack and nothing is lost either way.")
                .define("enabled", true);
        builder.pop();
    }

    /** What the config says; true (on) if the config is not loaded (it always is, once the server starts). */
    public static boolean enabled() {
        try {
            return enabled == null || enabled.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
