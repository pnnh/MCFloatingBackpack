package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;

/**
 * Client asks to open or close the floating panel for a claimed backpack ref.
 * Server re-resolves, syncs contents when opening, and acks visibility.
 */
public record BackpackOpenRequestPayload(boolean wantOpen, int sourceOrdinal, int invIndex)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackOpenRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "open_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackOpenRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    BackpackOpenRequestPayload::wantOpen,
                    ByteBufCodecs.VAR_INT,
                    BackpackOpenRequestPayload::sourceOrdinal,
                    ByteBufCodecs.VAR_INT,
                    BackpackOpenRequestPayload::invIndex,
                    BackpackOpenRequestPayload::new);

    public static BackpackOpenRequestPayload toggleMainHand() {
        return new BackpackOpenRequestPayload(
                true, BackpackRef.Source.MAIN_HAND.ordinal(), -1);
    }

    public static BackpackOpenRequestPayload open(BackpackRef ref) {
        return new BackpackOpenRequestPayload(true, ref.source().ordinal(), ref.invIndex());
    }

    public static BackpackOpenRequestPayload close() {
        return new BackpackOpenRequestPayload(false, 0, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackOpenRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (!payload.wantOpen()) {
                BackpackNetwork.sendSession(serverPlayer, false, null);
                return;
            }
            BackpackRef resolved = BackpackRef.resolve(serverPlayer);
            if (resolved == null) {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.mcfloatingbackpack.need_backpack"), true);
                BackpackNetwork.sendSession(serverPlayer, false, null);
                return;
            }
            BackpackNetwork.sendSession(serverPlayer, true, resolved);
        });
    }
}
