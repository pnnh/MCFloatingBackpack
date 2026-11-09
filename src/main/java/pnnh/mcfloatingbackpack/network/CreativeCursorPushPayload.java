package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.creative.CreativeCursorClient;

/**
 * 027: the server tells the client what its cursor is now, after a panel operation changed it. The game's own cursor
 * update is ignored by the client while the creative inventory screen is open, so this one is our own.
 */
public record CreativeCursorPushPayload(ItemStack cursor) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CreativeCursorPushPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "creative_cursor_push"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeCursorPushPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, CreativeCursorPushPayload::cursor, CreativeCursorPushPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(CreativeCursorPushPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CreativeCursorClient.onPush(payload.cursor()));
    }
}
