package pnnh.mcfloatingbackpack.network;

import java.util.Optional;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedMembers;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedSessions;

/**
 * 065: the client asks to open the combined panel ({@link #OPEN}, the combined key; 066: that opens the "all" tab) or to switch it to another tab ({@link #SELECT}) or to the "all" tab ({@link #SELECT_ALL}). For
 * SELECT, {@code index} is the member's place in the table the client was shown and {@code identity} the identity it saw there (empty for a
 * backpack that had none); the server checks both against the table it works out now and refuses a request that no longer fits. Nothing the
 * client says is trusted beyond that.
 */
public record CombinedSelectPayload(byte op, int index, Optional<UUID> identity) implements CustomPacketPayload {
    public static final byte OPEN = 0;
    public static final byte SELECT = 1;
    /** 066: the "all" tab; {@code index} and {@code identity} mean nothing. */
    public static final byte SELECT_ALL = 2;

    public static final CustomPacketPayload.Type<CombinedSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "combined_select"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<UUID>> IDENTITY = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, CombinedSelectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.op());
                buf.writeVarInt(payload.index());
                IDENTITY.encode(buf, payload.identity());
            },
            buf -> {
                byte op = buf.readByte();
                int index = buf.readVarInt();
                Optional<UUID> identity = IDENTITY.decode(buf);
                if (op != OPEN && op != SELECT && op != SELECT_ALL) {
                    throw new DecoderException("combined select: unknown op " + op);
                }
                if (index < 0 || index >= CombinedMembers.MAX_MEMBERS) {
                    throw new DecoderException("combined select: member index out of range: " + index);
                }
                return new CombinedSelectPayload(op, index, identity);
            });

    public static CombinedSelectPayload open() {
        return new CombinedSelectPayload(OPEN, 0, Optional.empty());
    }

    public static CombinedSelectPayload selectAll() {
        return new CombinedSelectPayload(SELECT_ALL, 0, Optional.empty());
    }

    public static CombinedSelectPayload select(int index, UUID identity) {
        return new CombinedSelectPayload(SELECT, index, Optional.ofNullable(identity));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(CombinedSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.op() == OPEN) {
                CombinedSessions.open(player);
            } else if (payload.op() == SELECT_ALL) {
                CombinedSessions.selectAll(player);
            } else {
                CombinedSessions.select(player, payload.index(), payload.identity());
            }
        });
    }
}
