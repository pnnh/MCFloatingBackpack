package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.inventory.PanelArea;

public record BackpackSlotUpdatePayload(byte area, int slotIndex, ItemStack stack, ItemStack cursor)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackSlotUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "slot_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackSlotUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE,
                    BackpackSlotUpdatePayload::area,
                    ByteBufCodecs.VAR_INT,
                    BackpackSlotUpdatePayload::slotIndex,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackSlotUpdatePayload::stack,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackSlotUpdatePayload::cursor,
                    BackpackSlotUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackSlotUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            PanelArea area = PanelArea.fromByte(payload.area());
            if (area != null) {
                ClientBackpackView.get()
                        .applySlotUpdate(area, payload.slotIndex(), payload.stack(), payload.cursor());
            }
        });
    }
}
