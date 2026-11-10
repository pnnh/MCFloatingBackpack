package pnnh.mcfloatingbackpack.snapshot.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;

/** 031: {@code /mcfb snapshot gui} told the server; the server tells the client to open the window. Carries nothing. */
public record SnapshotOpenPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotOpenPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotOpenPayload> STREAM_CODEC =
            StreamCodec.unit(new SnapshotOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SnapshotOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(SnapshotClient::open);
    }
}
