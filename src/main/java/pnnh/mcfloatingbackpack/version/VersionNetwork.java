package pnnh.mcfloatingbackpack.version;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.diag.DiagLog.Side;
import pnnh.mcfloatingbackpack.diag.LogLevel;
import pnnh.mcfloatingbackpack.network.VersionHelloPayload;

/**
 * 039: the server's half of the version check, and what both sides need to know their own version. At login the server tells the
 * player which version of this mod it runs (an optional payload, only to a client that has it); the client compares and, if they
 * differ, says so in the player's own chat ({@code VersionClient}), and answers with its own version, which the server logs. A client
 * too old to have the payload gets a plain text from the server instead. Nothing here refuses or removes anyone: it only tells.
 */
public final class VersionNetwork {
    private VersionNetwork() {
    }

    /**
     * The version of this mod on this side, from the mod's metadata (which the release build takes from {@code mod_version}); null when
     * it cannot be read. A development switch can override it (see {@link VersionDebug}).
     */
    public static String localVersion(boolean serverSide) {
        String override = serverSide ? VersionDebug.serverVersionOverride() : VersionDebug.clientVersionOverride();
        if (override != null) {
            return override;
        }
        try {
            return ModList.get()
                    .getModContainerById(MCFloatingBackpack.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A player has logged in (server side). */
    public static void onLogin(ServerPlayer player) {
        // The owner of a single player game or of a LAN world is the server's own client: the versions are the same by construction.
        // Other players of a LAN world are not, and are told like anyone on a dedicated server.
        if (!VersionDebug.checkOwner() && player.server.isSingleplayerOwner(player.getGameProfile())) {
            return;
        }
        String serverVersion = localVersion(true);
        String name = player.getGameProfile().getName();
        // Never send the hello to a client that does not have it: NeoForge throws for a payload the other side cannot receive.
        if (VersionDebug.legacyClient() || !player.connection.hasChannel(VersionHelloPayload.TYPE)) {
            PlayerVersions.noVersionCheck(player.getUUID());
            player.sendSystemMessage(Component.literal(VersionNotices.legacyClientText(serverVersion)));
            DiagLog.log(
                    Side.SERVER,
                    LogLevel.WARN,
                    "{} has no MCFloatingBackpack version check (a version older than this feature); the server runs {}; told it to update",
                    name,
                    serverVersion);
            return;
        }
        PlayerVersions.expectReport(player.getUUID());
        PacketDistributor.sendToPlayer(player, new VersionHelloPayload(serverVersion == null ? "" : serverVersion));
    }

    /** A client has told the server its version (server side): remember it and log it. */
    public static void onReport(ServerPlayer player, String clientVersion) {
        // Only an answer to the hello is taken; anything else is ignored.
        if (!PlayerVersions.expectsReport(player.getUUID())) {
            return;
        }
        PlayerVersions.report(player.getUUID(), clientVersion);
        String serverVersion = localVersion(true);
        ModVersions.Result result = ModVersions.compare(clientVersion, serverVersion);
        String name = player.getGameProfile().getName();
        if (result == ModVersions.Result.MATCH) {
            DiagLog.log(Side.SERVER, LogLevel.DEBUG, "{} runs MCFloatingBackpack {} (matches the server)", name, clientVersion);
            return;
        }
        DiagLog.log(
                Side.SERVER,
                LogLevel.WARN,
                "Version mismatch: {} runs MCFloatingBackpack {} ({}), the server runs {}",
                name,
                clientVersion,
                result,
                serverVersion);
    }
}
