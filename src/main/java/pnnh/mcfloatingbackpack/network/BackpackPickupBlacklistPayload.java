package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklistCodecs;

/**
 * 024: server tells the client the whole pickup blacklist of the open backpack (after the session opens and after every
 * change). Kept apart from the session payload, whose codec already has as many fields as a composite allows.
 */
public record BackpackPickupBlacklistPayload(PickupBlacklist blacklist) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackPickupBlacklistPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "pickup_blacklist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackPickupBlacklistPayload> STREAM_CODEC =
            StreamCodec.composite(
                    PickupBlacklistCodecs.STREAM_CODEC,
                    BackpackPickupBlacklistPayload::blacklist,
                    BackpackPickupBlacklistPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackPickupBlacklistPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBackpackView.get().setPickupBlacklist(payload.blacklist()));
    }
}
