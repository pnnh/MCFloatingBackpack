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

/** 031: the window asks for the items of one snapshot (identified by its backpack and its time). */
public record SnapshotPreviewRequestPayload(UUID backpack, long time) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotPreviewRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_preview_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPreviewRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                SnapshotCodecs.writeUuid(buf, p.backpack());
                buf.writeVarLong(p.time());
            },
            buf -> new SnapshotPreviewRequestPayload(SnapshotCodecs.readUuid(buf), buf.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SnapshotPreviewRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SnapshotServer.onPreview(player, payload);
            }
        });
    }
}
