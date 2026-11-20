package pnnh.mcfloatingbackpack.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 058: what the server's operators are told about third-party compatibility: at every login, one line for each mod outside the verified
 * versions, and, once, when a compatibility failed and was switched off. An operator is a player with permission level 2
 * ({@code hasPermissions(2)}, the test the commands use; in a single player world that needs cheats). Nobody else is told, and the console
 * only has the log. The lines are in the language files.
 */
public final class ThirdPartyNotices {
    /** Permission level of an operator, the same as {@code SnapshotAccess.OP_LEVEL}. */
    static final int OP_LEVEL = 2;

    private ThirdPartyNotices() {
    }

    /** A player has logged in. */
    public static void onLogin(ServerPlayer player) {
        CompatRuntimeState state = ThirdPartyCompatService.state();
        if (state == null || !player.hasPermissions(OP_LEVEL)) {
            return;
        }
        for (CompatFinding finding : state.findingsOutOfRange()) {
            boolean forced = state.status(finding.entry().modId()).orElse(CompatStatus.OUT_OF_RANGE) == CompatStatus.FORCED;
            say(player, untested(finding, forced));
        }
    }

    static Component untested(CompatFinding finding, boolean forced) {
        return Component.translatable(
                "message.mcfloatingbackpack.compat.untested",
                finding.entry().displayName(),
                Component.translatable(forced ? "message.mcfloatingbackpack.compat.state.forced" : "message.mcfloatingbackpack.compat.state.off"),
                finding.subjectName(),
                finding.installedVersion(),
                Component.translatable(verdictKey(finding.verdict())),
                CompatLogText.ranges(finding.ranges()));
    }

    static String verdictKey(VersionVerdict verdict) {
        return "message.mcfloatingbackpack.compat.verdict." + switch (verdict) {
            case BELOW_ALL -> "below";
            case ABOVE_ALL -> "above";
            case IN_GAP -> "gap";
            case UNKNOWN, IN_RANGE -> "unknown";
        };
    }

    /** A bridge failed for the first time in this run: one ERROR line with the cause, one line to every operator who is online. */
    public static void failed(String modId, Throwable cause) {
        CompatRuntimeState state = ThirdPartyCompatService.state();
        String name = state == null ? modId : state.entry(modId).map(ThirdPartyCompat::displayName).orElse(modId);
        MCFloatingBackpack.LOGGER.error(CompatLogText.failure(name), cause);
        MinecraftServer server = ThirdPartyCompatService.server();
        if (server == null) {
            return;
        }
        Component text = Component.translatable("message.mcfloatingbackpack.compat.failed", name, cause.getClass().getSimpleName());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(OP_LEVEL)) {
                say(player, text);
            }
        }
    }

    private static void say(ServerPlayer player, Component text) {
        player.sendSystemMessage(Component.translatable("message.mcfloatingbackpack.prefix")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(text.copy().withStyle(ChatFormatting.YELLOW)));
    }
}
