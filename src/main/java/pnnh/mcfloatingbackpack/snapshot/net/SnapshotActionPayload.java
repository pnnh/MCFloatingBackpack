package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.UUID;

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
 * 031: an action of permission level 2 (restore, delete, clean-up). The server checks the permission and every field again
 * and works from the files as they are now; nothing the client says about what it saw is trusted.
 *
 * @param target for a restore: the online player to give the new backpack to, or empty for the requester
 * @param days for a clean-up: how many days (0 = all)
 */
public record SnapshotActionPayload(byte kind, UUID backpack, long time, String target, int days) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "snapshot_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind());
                SnapshotCodecs.writeUuid(buf, p.backpack());
                buf.writeVarLong(p.time());
                buf.writeUtf(p.target(), SnapshotRequestRules.MAX_NAME);
                buf.writeVarInt(p.days());
            },
            buf -> new SnapshotActionPayload(
                    buf.readByte(),
                    SnapshotCodecs.readUuid(buf),
                    buf.readVarLong(),
                    buf.readUtf(SnapshotRequestRules.MAX_NAME),
                    buf.readVarInt()));

    public static SnapshotActionPayload restore(UUID backpack, long time, String target) {
        return new SnapshotActionPayload(ActionKind.RESTORE.code(), backpack, time, target, 0);
    }

    public static SnapshotActionPayload deleteOne(UUID backpack, long time) {
        return new SnapshotActionPayload(ActionKind.DELETE_ONE.code(), backpack, time, "", 0);
    }

    public static SnapshotActionPayload deleteAll(UUID backpack) {
        return new SnapshotActionPayload(ActionKind.DELETE_ALL.code(), backpack, 0L, "", 0);
    }

    public static SnapshotActionPayload trim(ActionKind kind, int days) {
        return new SnapshotActionPayload(kind.code(), new UUID(0L, 0L), 0L, "", days);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SnapshotActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SnapshotServer.onAction(player, payload);
            }
        });
    }
}
