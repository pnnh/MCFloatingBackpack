package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.version.VersionNetwork;

/**
 * 039: the client answers the server's {@link VersionHelloPayload} with the version of this mod it runs, so that the server can log it.
 * Optional, like the hello. Nothing in the game depends on it: the server never refuses or removes anyone because of it.
 */
public record VersionReportPayload(String clientVersion) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VersionReportPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "version_report"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VersionReportPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(256), VersionReportPayload::clientVersion, VersionReportPayload::new);

    public VersionReportPayload {
        clientVersion = clientVersion == null ? "" : clientVersion.length() > VersionHelloPayload.MAX_LENGTH
                ? clientVersion.substring(0, VersionHelloPayload.MAX_LENGTH)
                : clientVersion;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(VersionReportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                VersionNetwork.onReport(player, payload.clientVersion());
            }
        });
    }
}
