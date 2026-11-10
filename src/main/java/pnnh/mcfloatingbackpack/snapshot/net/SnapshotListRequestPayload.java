package pnnh.mcfloatingbackpack.snapshot.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;
import pnnh.mcfloatingbackpack.snapshot.SnapshotServer;

/**
 * 031: the window asks for one page of the list. The server decides what the requester may see; {@code scope}, {@code owner} and
 * {@code inactiveDays} count for permission level 2 only.
 */
public record SnapshotListRequestPayload(byte scope, String owner, int inactiveDays, int page, int pageSize)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotListRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotListRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.scope());
                buf.writeUtf(p.owner(), SnapshotRequestRules.MAX_NAME);
                buf.writeVarInt(p.inactiveDays());
                buf.writeVarInt(p.page());
                buf.writeVarInt(p.pageSize());
            },
            buf -> new SnapshotListRequestPayload(
                    buf.readByte(),
                    buf.readUtf(SnapshotRequestRules.MAX_NAME),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SnapshotListRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SnapshotServer.onList(player, payload);
            }
        });
    }
}
