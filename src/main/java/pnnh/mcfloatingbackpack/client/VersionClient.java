package pnnh.mcfloatingbackpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.network.VersionReportPayload;
import pnnh.mcfloatingbackpack.version.ModVersions;
import pnnh.mcfloatingbackpack.version.VersionNetwork;
import pnnh.mcfloatingbackpack.version.VersionNotices;
import pnnh.mcfloatingbackpack.version.VersionNotices.Notice;

/**
 * 039: the client's half of the version check. The server's hello says which version it runs; if it is not the same as this one
 * (MAJOR.MINOR, see {@code ModVersions}) the player gets one line in their own chat, shown on the next client tick (so that it is not
 * lost to the moment of logging in), once for each login. The client always answers with its own version.
 */
@EventBusSubscriber(modid = MCFloatingBackpack.MOD_ID, value = Dist.CLIENT)
public final class VersionClient {
    private record Pending(Notice notice, String clientVersion, String serverVersion) {
    }

    private static Pending pending;

    private VersionClient() {
    }

    /** The server's hello has arrived (on the client's main thread). */
    public static void onHello(String serverVersion) {
        String mine = VersionNetwork.localVersion(false);
        Notice notice = VersionNotices.noticeFor(ModVersions.compare(mine, serverVersion));
        pending = notice == Notice.SILENT ? null : new Pending(notice, mine, serverVersion);
        PacketDistributor.sendToServer(new VersionReportPayload(mine == null ? "" : mine));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Pending now = pending;
        Minecraft minecraft = Minecraft.getInstance();
        if (now == null || minecraft.player == null || minecraft.gui == null) {
            return;
        }
        pending = null;
        String key = switch (now.notice()) {
            case OLDER -> "message.mcfloatingbackpack.version.older";
            case NEWER -> "message.mcfloatingbackpack.version.newer";
            default -> "message.mcfloatingbackpack.version.unknown";
        };
        FloatingPanelEvents.chat(minecraft, Component.translatable(key, shown(now.clientVersion()), shown(now.serverVersion())));
    }

    /** What is left of this login is gone with it. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = null;
    }

    private static String shown(String version) {
        return version == null || version.isBlank() ? "?" : version;
    }
}
