package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.List;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;

/**
 * 031: the versions of one backpack, newest first; empty if it is gone or not one the requester may see. 057: one page of them, limited to a
 * kind ({@code kind}: 0 for all, else the byte of the kind the server used); {@code total} is how many there are of that kind, in all pages.
 */
public record SnapshotVersionsPayload(UUID backpack, byte kind, int page, int pages, int total, List<VersionRow> versions)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotVersionsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_versions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotVersionsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                SnapshotCodecs.writeUuid(buf, p.backpack());
                buf.writeByte(p.kind());
                buf.writeVarInt(p.page());
                buf.writeVarInt(p.pages());
                buf.writeVarInt(p.total());
                SnapshotCodecs.writeVersions(buf, p.versions());
            },
            buf -> {
                UUID backpack = SnapshotCodecs.readUuid(buf);
                byte kind = buf.readByte();
                if (!SnapshotRequestRules.validKind(kind)) {
                    throw new DecoderException("unknown snapshot kind filter: " + kind);
                }
                int page = buf.readVarInt();
                int pages = buf.readVarInt();
                int total = buf.readVarInt();
                if (page < 1 || pages < 1 || total < 0 || total > SnapshotRequestRules.MAX_VERSIONS) {
                    throw new DecoderException("snapshot version page out of range");
                }
                return new SnapshotVersionsPayload(backpack, kind, page, pages, total, SnapshotCodecs.readVersions(buf));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SnapshotVersionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SnapshotClient.onVersions(payload));
    }
}
