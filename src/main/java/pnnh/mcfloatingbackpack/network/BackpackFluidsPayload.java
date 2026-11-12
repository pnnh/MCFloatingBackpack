package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.tank.FluidStore;
import pnnh.mcfloatingbackpack.tank.FluidStoreCodecs;

/**
 * 048: server tells the client what the open backpack holds of each liquid (after the session opens and, from 049 on, after every
 * change). Kept apart from the session payload, like the tags and the pickup blacklist: the panel has no backpack stack to read it from.
 */
public record BackpackFluidsPayload(FluidStore fluids) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackFluidsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "fluids"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackFluidsPayload> STREAM_CODEC =
            StreamCodec.composite(FluidStoreCodecs.STREAM_CODEC, BackpackFluidsPayload::fluids, BackpackFluidsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackFluidsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBackpackView.get().setFluids(payload.fluids()));
    }
}
