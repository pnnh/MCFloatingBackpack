package pnnh.mcfloatingbackpack.diag;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config (the world's {@code serverconfig/mcfloatingbackpack-server.toml}): the log level of this mod's server-side
 * diagnostics. The client's own level is in the client config ({@code CompatConfig.LOG_LEVEL}).
 */
public final class DiagConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<LogLevel> SERVER_LEVEL;

    public static final ModConfigSpec SERVER_SPEC;

    static {
        BUILDER.push("logging");
        SERVER_LEVEL = defineLevel(BUILDER, "server", LogLevel.INFO);
        BUILDER.pop();
        SERVER_SPEC = BUILDER.build();
    }

    private DiagConfig() {
    }

    /** The {@code level} entry, the same wording in both configs. */
    public static ModConfigSpec.EnumValue<LogLevel> defineLevel(ModConfigSpec.Builder builder, String side, LogLevel fallback) {
        return builder.comment(
                        "How much of this mod's own diagnostics the " + side + " writes to the log (latest.log).",
                        "  OFF   - none.",
                        "  ERROR - only things that went wrong and lost something.",
                        "  WARN  - also suspicious things, for example a backpack that disappeared from a player's inventory",
                        "          (the log then lists the player's last panel actions).",
                        "  INFO  - also notable events that are not wrong, for example a backpack that was dropped.",
                        "  DEBUG - also every panel click and creative-cursor message. A lot of lines; use it to chase a bug.",
                        "The last panel actions of each player are always kept in memory, whatever this is set to.")
                .translation("config.mcfloatingbackpack.logLevel")
                .defineEnum("level", fallback);
    }
}
