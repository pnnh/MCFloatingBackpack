package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.VersionClient;

/**
 * 039: the server tells a client the version of this mod it runs. Registered as an <em>optional</em> payload of its own (version "1",
 * which never changes), so a client that does not have it can still log in; the server only sends it to a client that has it. See
 * {@code VersionNetwork}.
 */
public record VersionHelloPayload(String serverVersion) implements CustomPacketPayload {
    /** The longest version string sent; a longer one is cut off, so that a version string can never be what breaks a connection. */
    public static final int MAX_LENGTH = 64;

    public static final CustomPacketPayload.Type<VersionHelloPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "version_hello"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VersionHelloPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(256), VersionHelloPayload::serverVersion, VersionHelloPayload::new);

    public VersionHelloPayload {
        serverVersion = serverVersion == null ? "" : serverVersion.length() > MAX_LENGTH ? serverVersion.substring(0, MAX_LENGTH) : serverVersion;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(VersionHelloPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> VersionClient.onHello(payload.serverVersion()));
    }
}
