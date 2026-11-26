package pnnh.mcfloatingbackpack.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.quick.QuickAccessScreen;

/**
 * 067: whether the swap was written. The client closes the grid only when {@code ok} is true.
 */
public record QuickSwapResultPayload(boolean ok) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuickSwapResultPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "quick_swap_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickSwapResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, QuickSwapResultPayload::ok, QuickSwapResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(QuickSwapResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen instanceof QuickAccessScreen screen) {
                screen.onResult(payload.ok());
            }
        });
    }
}
