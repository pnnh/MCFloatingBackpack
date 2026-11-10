package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;

/** 031: the versions of one backpack, newest first; empty if it is gone or not one the requester may see. */
public record SnapshotVersionsPayload(UUID backpack, List<VersionRow> versions) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotVersionsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_versions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotVersionsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                SnapshotCodecs.writeUuid(buf, p.backpack());
                SnapshotCodecs.writeVersions(buf, p.versions());
            },
            buf -> new SnapshotVersionsPayload(SnapshotCodecs.readUuid(buf), SnapshotCodecs.readVersions(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SnapshotVersionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SnapshotClient.onVersions(payload));
    }
}
