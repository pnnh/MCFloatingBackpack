package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView;

/**
 * 066: one cell of the "all" tab changed (a click, an auto pickup), and what the in-memory cursor is now. {@code flat} is -1 when only the
 * cursor changed. Dropped by the client if its epoch is not the one the tab is ready with.
 */
public record CombinedCellUpdatePayload(int epoch, int flat, ItemStack stack, ItemStack cursor) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CombinedCellUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "combined_cell_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombinedCellUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombinedCellUpdatePayload::epoch,
            ByteBufCodecs.VAR_INT, p -> p.flat() + 1,
            ItemStack.OPTIONAL_STREAM_CODEC, CombinedCellUpdatePayload::stack,
            ItemStack.OPTIONAL_STREAM_CODEC, CombinedCellUpdatePayload::cursor,
            (epoch, flatPlusOne, stack, cursor) -> new CombinedCellUpdatePayload(epoch, flatPlusOne - 1, stack, cursor));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(CombinedCellUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCombinedView.get().onCellUpdate(payload));
    }
}
