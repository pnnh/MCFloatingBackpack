package pnnh.mcfloatingbackpack.snapshot.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import io.netty.handler.codec.DecoderException;
import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;

/** 031: how an action turned out: a code and its plain arguments (what they are depends on the code). */
public record SnapshotResultPayload(ResultCode code, String a, String b, int n1, int n2) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotResultPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.code().code());
                buf.writeUtf(p.a(), 64);
                buf.writeUtf(p.b(), SnapshotRequestRules.MAX_NAME);
                buf.writeVarInt(p.n1());
                buf.writeVarInt(p.n2());
            },
            buf -> {
                ResultCode code = ResultCode.fromByte(buf.readByte());
                if (code == null) {
                    throw new DecoderException("unknown snapshot result code");
                }
                return new SnapshotResultPayload(code, buf.readUtf(64), buf.readUtf(SnapshotRequestRules.MAX_NAME), buf.readVarInt(), buf.readVarInt());
            });

    public static SnapshotResultPayload of(ResultCode code) {
        return new SnapshotResultPayload(code, "", "", 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SnapshotResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SnapshotClient.onResult(payload));
    }
}
