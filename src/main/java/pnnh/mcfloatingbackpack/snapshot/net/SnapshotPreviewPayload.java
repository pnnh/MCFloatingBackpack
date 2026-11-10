package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.List;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotClient;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;

/** 031: the main-area items of one snapshot (at most 108), or the reason there are none ({@code reason} is set when {@code items} is not). */
public record SnapshotPreviewPayload(UUID backpack, long time, ResultCode reason, int capacity, int slots, List<ItemStack> items)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotPreviewPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_preview"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> ITEMS =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(SnapshotRequestRules.MAX_PREVIEW_SLOTS));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                SnapshotCodecs.writeUuid(buf, p.backpack());
                buf.writeVarLong(p.time());
                boolean ok = p.reason() == null;
                buf.writeBoolean(ok);
                if (ok) {
                    buf.writeVarInt(p.capacity());
                    buf.writeVarInt(p.slots());
                    ITEMS.encode(buf, p.items());
                } else {
                    buf.writeByte(p.reason().code());
                }
            },
            buf -> {
                UUID backpack = SnapshotCodecs.readUuid(buf);
                long time = buf.readVarLong();
                if (buf.readBoolean()) {
                    return new SnapshotPreviewPayload(backpack, time, null, buf.readVarInt(), buf.readVarInt(), ITEMS.decode(buf));
                }
                ResultCode reason = ResultCode.fromByte(buf.readByte());
                if (reason == null) {
                    throw new DecoderException("unknown snapshot preview failure");
                }
                return new SnapshotPreviewPayload(backpack, time, reason, 0, 0, List.of());
            });

    public static SnapshotPreviewPayload failed(UUID backpack, long time, ResultCode reason) {
        return new SnapshotPreviewPayload(backpack, time, reason, 0, 0, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SnapshotPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SnapshotClient.onPreview(payload));
    }
}
