package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.creative.CreativeCursorBridge;

/**
 * 027: the client tells the server what is on its cursor. In the creative inventory screen the cursor exists only on the
 * client (the clicks are handled there and no container click is sent), but the panel's cursor operations work on the server's
 * menu cursor, so the server mirrors it. Only a creative player's report is taken, and it is checked.
 */
public record CreativeCursorReportPayload(ItemStack cursor) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CreativeCursorReportPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "creative_cursor_report"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeCursorReportPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, CreativeCursorReportPayload::cursor, CreativeCursorReportPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(CreativeCursorReportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CreativeCursorBridge.onReport(player, payload.cursor());
            }
        });
    }
}
