package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;

/**
 * 031: one page of the list, with whether the requester has permission level 2. The flag only decides which controls the
 * window shows; it authorises nothing.
 */
public record SnapshotListPayload(boolean op, byte scope, int page, int pages, int total, List<Row> rows)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotListPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.op());
                buf.writeByte(p.scope());
                buf.writeVarInt(p.page());
                buf.writeVarInt(p.pages());
                buf.writeVarInt(p.total());
                SnapshotCodecs.writeRows(buf, p.rows());
            },
            buf -> new SnapshotListPayload(
                    buf.readBoolean(),
                    buf.readByte(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    SnapshotCodecs.readRows(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SnapshotListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SnapshotClient.onList(payload));
    }
}
