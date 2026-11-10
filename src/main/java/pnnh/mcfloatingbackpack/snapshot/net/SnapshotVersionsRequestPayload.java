package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.snapshot.SnapshotServer;

/** 031: the window asks for the versions of one backpack. */
public record SnapshotVersionsRequestPayload(UUID backpack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotVersionsRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_versions_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotVersionsRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> SnapshotCodecs.writeUuid(buf, p.backpack()),
            buf -> new SnapshotVersionsRequestPayload(SnapshotCodecs.readUuid(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SnapshotVersionsRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SnapshotServer.onVersions(player, payload);
            }
        });
    }
}
