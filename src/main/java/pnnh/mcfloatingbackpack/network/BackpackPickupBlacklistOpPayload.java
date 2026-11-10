package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklistRequest;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklistRequest.Op;
import pnnh.mcfloatingbackpack.inventory.tags.TagRules;

/**
 * 024: client asks to add a tag to, or remove it from, the pickup blacklist of the player's active backpack. Any check that
 * fails drops the whole request and changes nothing (the client is then sent what the blacklist really is): the operation
 * and the tag must be valid, and the backpack's left area must hold an auto pickup. Adding a tag that is already listed
 * is not an error and changes nothing; adding to a full list is refused.
 */
public record BackpackPickupBlacklistOpPayload(byte op, String tag) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackPickupBlacklistOpPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "pickup_blacklist_op"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackPickupBlacklistOpPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.op());
                        buf.writeUtf(payload.tag(), TagRules.MAX_LENGTH);
                    },
                    buf -> new BackpackPickupBlacklistOpPayload(buf.readByte(), buf.readUtf(TagRules.MAX_LENGTH)));

    public static BackpackPickupBlacklistOpPayload of(Op op, String tag) {
        return new BackpackPickupBlacklistOpPayload(op.toByte(), tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackPickupBlacklistOpPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BackpackRef ref = BackpackRef.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // 026: every request into a backpack goes through the registry first.
            BackpackInventory backpack = BackpackGate.acquireForPanel(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            Op op = Op.fromByte(payload.op());
            // The blacklist only works with an auto pickup in the left area: no tool there, no request.
            if (!backpack.hasAutoPickup() || !PickupBlacklistRequest.validate(op, payload.tag())) {
                BackpackNetwork.sendPickupBlacklist(player, backpack);
                return;
            }
            PickupBlacklist current = backpack.pickupBlacklist();
            PickupBlacklist next = op == Op.ADD ? current.add(payload.tag()) : current.remove(payload.tag());
            if (next == null) {
                BackpackNetwork.sendPickupBlacklist(player, backpack);
                return;
            }
            if (!next.equals(current)) {
                backpack.setPickupBlacklist(next);
                ref.saveInventory(player, backpack);
            }
            BackpackNetwork.sendPickupBlacklist(player, backpack);
        });
    }
}
